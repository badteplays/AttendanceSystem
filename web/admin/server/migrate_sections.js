const admin = require('firebase-admin');
const fs = require('fs');

if (!admin.apps.length) {
  const serviceAccount = JSON.parse(fs.readFileSync('serviceAccountKey.json', 'utf8'));
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

const db = admin.firestore();

function getNewSectionStr(oldStr) {
  if (!oldStr) return oldStr;
  return oldStr.replace(/10([1-5])/g, '30$1').replace(/20([1-5])/g, '40$1');
}

async function migrate() {
  console.log("Starting migration...");
  let count = 0;

  // 1. Migrate users
  const usersSnap = await db.collection('users').get();
  const userBatch = db.batch();
  usersSnap.forEach(doc => {
    const data = doc.data();
    if (data.section) {
      const newSec = getNewSectionStr(data.section);
      if (newSec !== data.section) {
        userBatch.update(doc.ref, { section: newSec });
        count++;
      }
    }
  });
  if (count > 0) {
    await userBatch.commit();
    console.log(`Updated ${count} users.`);
  }

  // 2. Migrate schedules
  count = 0;
  let roomCount = 0;
  const schedSnap = await db.collection('schedules').get();
  const schedBatch = db.batch();
  schedSnap.forEach(doc => {
    const data = doc.data();
    let updates = {};
    if (data.section) {
      const newSec = getNewSectionStr(data.section);
      if (newSec !== data.section) {
        updates.section = newSec;
      }
    }
    if ((data.subject === 'PE' || data.subject === 'P.E' || data.subject === 'Physical Education') && data.room) {
      if (data.room !== 'P.E AREA') {
        updates.room = 'P.E AREA';
        roomCount++;
      }
    }
    if (Object.keys(updates).length > 0) {
      schedBatch.update(doc.ref, updates);
      count++;
    }
  });
  if (count > 0) {
    await schedBatch.commit();
    console.log(`Updated ${count} schedules (including ${roomCount} P.E room changes).`);
  }

  // 3. Migrate attendance
  count = 0;
  const attSnap = await db.collection('attendance').get();
  const attBatches = [];
  let currentBatch = db.batch();
  let batchCount = 0;
  
  attSnap.forEach(doc => {
    const data = doc.data();
    if (data.section) {
      const newSec = getNewSectionStr(data.section);
      if (newSec !== data.section) {
        currentBatch.update(doc.ref, { section: newSec });
        count++;
        batchCount++;
        if (batchCount === 490) {
          attBatches.push(currentBatch.commit());
          currentBatch = db.batch();
          batchCount = 0;
        }
      }
    }
  });
  if (batchCount > 0) {
    attBatches.push(currentBatch.commit());
  }
  if (attBatches.length > 0) {
    await Promise.all(attBatches);
    console.log(`Updated ${count} active attendance records.`);
  }

  // 4. Migrate archived attendance
  count = 0;
  const archSnap = await db.collection('archived_attendance').get();
  const archBatches = [];
  currentBatch = db.batch();
  batchCount = 0;

  archSnap.forEach(doc => {
    const data = doc.data();
    if (data.section) {
      const newSec = getNewSectionStr(data.section);
      if (newSec !== data.section) {
        currentBatch.update(doc.ref, { section: newSec });
        count++;
        batchCount++;
        if (batchCount === 490) {
          archBatches.push(currentBatch.commit());
          currentBatch = db.batch();
          batchCount = 0;
        }
      }
    }
  });
  if (batchCount > 0) {
    archBatches.push(currentBatch.commit());
  }
  if (archBatches.length > 0) {
    await Promise.all(archBatches);
    console.log(`Updated ${count} archived attendance records.`);
  }

  console.log("Migration complete.");
}

migrate().catch(console.error);
