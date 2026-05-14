const admin = require('firebase-admin');
const fs = require('fs');

if (!admin.apps.length) {
  const sa = JSON.parse(fs.readFileSync('serviceAccountKey.json', 'utf8'));
  admin.initializeApp({ credential: admin.credential.cert(sa) });
}
const db = admin.firestore();

async function verify() {
  const snap = await db.collection('schedules').get();
  let errors = 0;
  let total = 0;

  // Define rules
  const ICT_SUBJECTS = ['Java Programming - CompProg 1', 'Database Management - CompProg 2', 'Web Development - WebDev 1', 'Stylesheet Design - WebDev 2'];
  const SCIENCE_SUBJECTS = ['General Chemistry - GenChem', 'General Physics - GenPhys', 'Earth and Life Science - ELS'];

  const byTeacher = {};

  for (const doc of snap.docs) {
    const d = doc.data();
    total++;
    const strand = d.section.split('-')[0];

    // Rule 1: ICT subjects MUST be MAWD only
    if (ICT_SUBJECTS.includes(d.subject) && strand !== 'MAWD') {
      console.log(`❌ ICT VIOLATION: ${d.subject} assigned to ${d.section} (should be MAWD only)`);
      errors++;
    }

    // Rule 2: Science subjects MUST be STEM only
    if (SCIENCE_SUBJECTS.includes(d.subject) && strand !== 'STEM') {
      console.log(`❌ SCIENCE VIOLATION: ${d.subject} assigned to ${d.section} (should be STEM only)`);
      errors++;
    }

    // Rule 3: No schedule before 7AM or after 5PM
    const startHour = parseInt(d.startTime.split(':')[0]);
    if (startHour < 6 || startHour >= 20) {
      console.log(`❌ TIME VIOLATION: ${d.startTime} for ${d.teacherName}`);
      errors++;
    }

    // Rule 4: No lunch hour (12:00)
    if (startHour === 12) {
      console.log(`❌ LUNCH VIOLATION: Class at 12:00 for ${d.teacherName}`);
      errors++;
    }

    // Collect for summary
    if (!byTeacher[d.teacherName]) byTeacher[d.teacherName] = { dept: d.department, subjects: new Set(), strands: new Set(), count: 0 };
    byTeacher[d.teacherName].subjects.add(d.subject);
    byTeacher[d.teacherName].strands.add(strand);
    byTeacher[d.teacherName].count++;
  }

  console.log(`\n=== VERIFICATION SUMMARY ===`);
  console.log(`Total schedules: ${total}`);
  console.log(`Errors found: ${errors}`);
  console.log(`\n--- Teacher Breakdown ---`);
  for (const [name, info] of Object.entries(byTeacher)) {
    console.log(`\n${name} (${info.dept}) — ${info.count} classes`);
    console.log(`  Subjects: ${[...info.subjects].join(', ')}`);
    console.log(`  Strands:  ${[...info.strands].join(', ')}`);
  }

  if (errors === 0) {
    console.log('\n✅✅✅ ALL CHECKS PASSED — ZERO VIOLATIONS ✅✅✅');
  } else {
    console.log(`\n🚨 ${errors} VIOLATIONS FOUND`);
  }

  process.exit(0);
}

verify().catch(e => { console.error(e); process.exit(1); });
