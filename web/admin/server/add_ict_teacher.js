const admin = require('firebase-admin');
const fs = require('fs');

if (!admin.apps.length) {
  const serviceAccount = JSON.parse(fs.readFileSync('serviceAccountKey.json', 'utf8'));
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
}

const db = admin.firestore();

async function createIctTeacher() {
  const email = "ict.teacher@school.edu";
  const password = "teacher123";
  const name = "Mr. Alan Turing";
  const department = "ICT";

  let userRecord;
  try {
    userRecord = await admin.auth().getUserByEmail(email);
    console.log("Teacher already exists in Auth, updating...");
  } catch (e) {
    if (e.code === 'auth/user-not-found') {
      userRecord = await admin.auth().createUser({
        email: email,
        password: password,
        displayName: name,
      });
      console.log("Created teacher in Auth.");
    } else {
      throw e;
    }
  }

  const uid = userRecord.uid;

  // Add to firestore users
  await db.collection('users').doc(uid).set({
    email: email,
    name: name,
    role: "teacher",
    department: department,
    isTeacher: true,
    isStudent: false,
    createdAt: Date.now()
  });

  console.log("Saved teacher to users collection.");

  // Clear existing schedules for this teacher
  const existingSchedules = await db.collection('schedules').where('teacherId', '==', uid).get();
  const deleteBatch = db.batch();
  existingSchedules.forEach(doc => deleteBatch.delete(doc.ref));
  await deleteBatch.commit();
  console.log(`Cleared ${existingSchedules.size} existing schedules.`);

  // Generate new schedule
  const daysOfWeek = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
  const dayOff = daysOfWeek[Math.floor(Math.random() * daysOfWeek.length)];
  const workingDays = daysOfWeek.filter(d => d !== dayOff);
  
  const subjects = ["HTML", "Java", "CSS"];
  const rooms = ["Comlab203", "Comlab303"];
  // Random pool of MAWD sections
  const sections = ["MAWD-301", "MAWD-302", "MAWD-303", "MAWD-304", "MAWD-305", "MAWD-401", "MAWD-402", "MAWD-403", "MAWD-404", "MAWD-405"];

  const batch = db.batch();
  let schedCount = 0;

  for (const day of workingDays) {
    const dailyTimes = [
      { start: "08:00", end: "10:00" }, // Class 1
      // 10:00 - 11:00 Break
      { start: "11:00", end: "13:00" }, // Class 2
      // 13:00 - 14:00 Break
      { start: "14:00", end: "16:00" }  // Class 3
    ];

    for (let i = 0; i < 3; i++) {
      const subject = subjects[i];
      const time = dailyTimes[i];
      const room = rooms[Math.floor(Math.random() * rooms.length)];
      const section = sections[Math.floor(Math.random() * sections.length)];

      const docRef = db.collection('schedules').doc();
      batch.set(docRef, {
        teacherId: uid,
        teacherName: name,
        subject: subject,
        section: section,
        day: day,
        startTime: time.start,
        endTime: time.end,
        room: room,
        createdAt: Date.now(),
        isDemo: true // So it can be cleaned up if needed
      });
      schedCount++;
    }
  }

  await batch.commit();
  console.log(`Created ${schedCount} schedule blocks across 5 working days (Day Off: ${dayOff}).`);
  console.log("Done.");
}

createIctTeacher().catch(console.error);
