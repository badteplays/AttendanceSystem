const admin = require('firebase-admin');
const fs = require('fs');

if (!admin.apps.length) {
  const serviceAccount = JSON.parse(fs.readFileSync('serviceAccountKey.json', 'utf8'));
  admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
}
const db = admin.firestore();

// ─── Subject Mapping: old → "Subject Name - Subject Code" ───
const SUBJECT_MAP = {
  // ICT subjects
  'JAVA':     'Java Programming - CompProg 1',
  'SQL':      'Database Management - CompProg 2',
  'HTML':     'Web Development - WebDev 1',
  'CSS':      'Stylesheet Design - WebDev 2',

  // Science
  'CHEM':     'General Chemistry - GenChem',
  'PHYSICS':  'General Physics - GenPhys',
  'Science':  'Earth and Life Science - ELS',

  // Math
  'GENMATH':  'General Mathematics - GenMath',
  'STAT':     'Statistics & Probability - StatProb',
  'Math':     'General Mathematics - GenMath',

  // English
  'ENG12':    'English for Academic Purposes - EAP',
  'EAPP':     'English for Professionals - EAPP',
  'English':  'English Communication - EngComm',

  // Filipino
  'FIL12':    'Komunikasyon at Pananaliksik - KomPan',
  'PPTP':     'Pagbasa at Pagsusuri - PagPag',
  'Filipino': 'Filipino sa Piling Larang - FilPL',

  // PE
  'HOPE4':    'Health Optimizing PE - HOPE 4',
  'PE4':      'Physical Education - PE 4',
  'PE':       'Physical Fitness - PE',
};

async function run() {
  const snap = await db.collection('schedules').get();
  console.log(`Found ${snap.size} schedule documents.`);

  let updated = 0;
  let skipped = 0;
  const batch_size = 500;
  let batch = db.batch();
  let batchCount = 0;

  for (const doc of snap.docs) {
    const data = doc.data();
    const oldSubject = data.subject;

    if (SUBJECT_MAP[oldSubject]) {
      batch.update(doc.ref, { subject: SUBJECT_MAP[oldSubject] });
      updated++;
      batchCount++;
      console.log(`  ✅ ${doc.id}: "${oldSubject}" → "${SUBJECT_MAP[oldSubject]}"`);

      if (batchCount >= batch_size) {
        await batch.commit();
        console.log(`  💾 Committed batch of ${batchCount}`);
        batch = db.batch();
        batchCount = 0;
      }
    } else {
      // Check if already migrated
      if (oldSubject && oldSubject.includes(' - ')) {
        skipped++;
      } else {
        console.log(`  ⚠️ Unknown subject: "${oldSubject}" in doc ${doc.id}`);
      }
    }
  }

  if (batchCount > 0) {
    await batch.commit();
    console.log(`  💾 Committed final batch of ${batchCount}`);
  }

  console.log(`\n=== DONE ===`);
  console.log(`Updated: ${updated}`);
  console.log(`Skipped (already migrated): ${skipped}`);
  process.exit(0);
}

run().catch(e => { console.error(e); process.exit(1); });
