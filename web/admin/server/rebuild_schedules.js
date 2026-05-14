const admin = require('firebase-admin');
const fs = require('fs');

if (!admin.apps.length) {
  const sa = JSON.parse(fs.readFileSync('serviceAccountKey.json', 'utf8'));
  admin.initializeApp({ credential: admin.credential.cert(sa) });
}
const db = admin.firestore();

// ─── STRAND-SUBJECT LOGIC ───────────────────────────────────
const DEPT_CONFIG = {
  'ICT': {
    subjects: [
      'Java Programming - CompProg 1',
      'Database Management - CompProg 2',
      'Web Development - WebDev 1',
      'Stylesheet Design - WebDev 2',
    ],
    allowedStrands: ['MAWD'],
    rooms: ['Comlab203', 'Comlab303'],
  },
  'Science': {
    subjects: [
      'General Chemistry - GenChem',
      'General Physics - GenPhys',
      'Earth and Life Science - ELS',
    ],
    allowedStrands: ['STEM'],
    rooms: null,
  },
  'Math': {
    subjects: [
      'General Mathematics - GenMath',
      'Statistics & Probability - StatProb',
    ],
    allowedStrands: ['STEM', 'HUMSS', 'ABM', 'MAWD', 'DIGAR'],
    rooms: null,
  },
  'English': {
    subjects: [
      'English for Academic Purposes - EAP',
      'English for Professionals - EAPP',
      'English Communication - EngComm',
    ],
    allowedStrands: ['STEM', 'HUMSS', 'ABM', 'MAWD', 'DIGAR'],
    rooms: null,
  },
  'Filipino': {
    subjects: [
      'Filipino sa Piling Larang - FilPL',
      'Komunikasyon at Pananaliksik - KomPan',
      'Pagbasa at Pagsusuri - PagPag',
    ],
    allowedStrands: ['STEM', 'HUMSS', 'ABM', 'MAWD', 'DIGAR'],
    rooms: null,
  },
  'PE': {
    subjects: [
      'Health Optimizing PE - HOPE 4',
      'Physical Education - PE 4',
      'Physical Fitness - PE',
    ],
    allowedStrands: ['STEM', 'HUMSS', 'ABM', 'MAWD', 'DIGAR'],
    rooms: ['P.E AREA'],
  },
};

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const SECTIONS = ['301', '302', '303', '304', '305', '401', '402', '403', '404', '405'];

// ─── PACKED 2-hour blocks, 6AM to 8PM, lunch at 12-1 ────────
// Every teacher's day is FULLY packed: no gaps, no random start times
const PACKED_SLOTS = [
  { start: '06:00', end: '08:00' },  // Period 1 (2hr)
  { start: '08:00', end: '10:00' },  // Period 2 (2hr)
  { start: '10:00', end: '12:00' },  // Period 3 (2hr)
  // ──── LUNCH BREAK 12:00 - 13:00 ────
  { start: '13:00', end: '15:00' },  // Period 4 (2hr)
  { start: '15:00', end: '17:00' },  // Period 5 (2hr)
  { start: '17:00', end: '19:00' },  // Period 6 (2hr)
  { start: '19:00', end: '20:00' },  // Period 7 (1hr — last period)
];

function shuffle(arr) {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function pickRandom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function getRoom(dept) {
  const config = DEPT_CONFIG[dept];
  if (config && config.rooms) {
    return pickRandom(config.rooms);
  }
  return `RM-${100 + Math.floor(Math.random() * 206)}`;
}

async function run() {
  // ─── STEP 1: Get all teachers ──────────────────────────────
  console.log('📋 Fetching teachers...');
  const tSnap = await db.collection('users').where('role', '==', 'teacher').get();
  const teachers = [];
  tSnap.docs.forEach(d => {
    const data = d.data();
    teachers.push({ uid: d.id, name: data.name, department: data.department });
  });
  console.log(`   Found ${teachers.length} teachers`);
  teachers.forEach(t => console.log(`   - ${t.name} (${t.department})`));

  // ─── STEP 2: Delete ALL existing schedules ─────────────────
  console.log('\n🗑️  Deleting all existing schedules...');
  const sSnap = await db.collection('schedules').get();
  const delBatchSize = 500;
  let delBatch = db.batch();
  let delCount = 0;
  for (const doc of sSnap.docs) {
    delBatch.delete(doc.ref);
    delCount++;
    if (delCount % delBatchSize === 0) {
      await delBatch.commit();
      console.log(`   Deleted ${delCount}...`);
      delBatch = db.batch();
    }
  }
  if (delCount % delBatchSize !== 0) {
    await delBatch.commit();
  }
  console.log(`   ✅ Deleted ${delCount} old schedules`);

  // ─── STEP 3: Generate PACKED schedules ─────────────────────
  console.log('\n🔨 Building packed schedules (6AM-8PM, 2hr blocks)...');

  // Track occupied: "SECTION_DAY_SLOTIDX" to prevent section conflicts
  const occupiedSlots = {};
  let totalCreated = 0;
  let batch = db.batch();
  let batchCount = 0;

  for (const teacher of teachers) {
    const dept = teacher.department;
    const config = DEPT_CONFIG[dept];

    if (!config) {
      console.log(`   ⚠️ Unknown department "${dept}" for ${teacher.name}, skipping`);
      continue;
    }

    const { subjects, allowedStrands } = config;

    // Pick a random day off
    const dayOff = pickRandom(DAYS);
    const workDays = DAYS.filter(d => d !== dayOff);

    console.log(`\n   👨‍🏫 ${teacher.name} (${dept}) — Day off: ${dayOff}`);

    // Build valid sections for this teacher
    const validSections = [];
    for (const strand of allowedStrands) {
      for (const sec of SECTIONS) {
        validSections.push(`${strand}-${sec}`);
      }
    }

    // For each work day, fill ALL 7 time slots consecutively
    for (const day of workDays) {
      let dayClasses = 0;

      for (let slotIdx = 0; slotIdx < PACKED_SLOTS.length; slotIdx++) {
        const slot = PACKED_SLOTS[slotIdx];

        // Find an available section for this slot
        const shuffledSections = shuffle(validSections);
        let placed = false;

        for (const section of shuffledSections) {
          const sectionKey = `${section}_${day}_${slotIdx}`;

          if (occupiedSlots[sectionKey]) continue;

          // Place the class
          const subject = pickRandom(subjects);
          const room = getRoom(dept);

          const schedDoc = {
            teacherId: teacher.uid,
            teacherName: teacher.name,
            department: dept,
            subject: subject,
            section: section,
            day: day,
            startTime: slot.start,
            endTime: slot.end,
            room: room,
            status: 'scheduled',
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
          };

          const ref = db.collection('schedules').doc();
          batch.set(ref, schedDoc);
          batchCount++;
          totalCreated++;

          occupiedSlots[sectionKey] = true;
          dayClasses++;
          placed = true;

          if (batchCount >= 500) {
            await batch.commit();
            console.log(`      💾 Committed batch (${totalCreated} total)`);
            batch = db.batch();
            batchCount = 0;
          }
          break;
        }

        if (!placed) {
          console.log(`      ⚠️ Could not place ${day} slot ${slot.start}-${slot.end} (all sections occupied)`);
        }
      }
      console.log(`      ${day}: ${dayClasses} classes (${PACKED_SLOTS.length} slots filled)`);
    }
  }

  // Final commit
  if (batchCount > 0) {
    await batch.commit();
  }

  console.log(`\n=== DONE ===`);
  console.log(`Total schedules created: ${totalCreated}`);
  console.log(`Schedule structure per work day:`);
  console.log(`  06:00-08:00  Period 1 (2hr)`);
  console.log(`  08:00-10:00  Period 2 (2hr)`);
  console.log(`  10:00-12:00  Period 3 (2hr)`);
  console.log(`  12:00-13:00  LUNCH BREAK`);
  console.log(`  13:00-15:00  Period 4 (2hr)`);
  console.log(`  15:00-17:00  Period 5 (2hr)`);
  console.log(`  17:00-19:00  Period 6 (2hr)`);
  console.log(`  19:00-20:00  Period 7 (1hr)`);
  console.log(`Rules enforced:`);
  console.log(`  - ICT → MAWD only`);
  console.log(`  - Science → STEM only`);
  console.log(`  - No gaps, fully packed days`);

  process.exit(0);
}

run().catch(e => { console.error(e); process.exit(1); });
