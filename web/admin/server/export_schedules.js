const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

let serviceAccount;
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
} else {
  serviceAccount = require('./serviceAccountKey.json');
}
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

const daysOrder = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

async function run() {
  const tSnap = await db.collection("users").where("role", "==", "teacher").get();
  const teachers = {};
  tSnap.docs.forEach(d => {
    teachers[d.id] = d.data().name;
  });

  const sSnap = await db.collection("schedules").get();
  const schedulesByTeacher = {};
  sSnap.docs.forEach(d => {
    const data = d.data();
    if (!schedulesByTeacher[data.teacherId]) {
      schedulesByTeacher[data.teacherId] = {};
    }
    if (!schedulesByTeacher[data.teacherId][data.day]) {
      schedulesByTeacher[data.teacherId][data.day] = [];
    }
    schedulesByTeacher[data.teacherId][data.day].push(data);
  });

  let output = "=== TEACHER SCHEDULES ===\n\n";

  for (const tId in teachers) {
    output += `👨‍🏫 ${teachers[tId]}\n`;
    output += `--------------------------------------------------\n`;
    const tScheds = schedulesByTeacher[tId] || {};

    for (const day of daysOrder) {
      if (!tScheds[day] || tScheds[day].length === 0) continue;

      output += `${day}:\n`;
      // Sort chronologically
      tScheds[day].sort((a, b) => a.startTime.localeCompare(b.startTime));

      for (const slot of tScheds[day]) {
        output += `  [ ${slot.startTime} - ${slot.endTime} ]  ${slot.subject} | ${slot.section} | ${slot.room}\n`;
      }
      output += `\n`;
    }
    output += `==================================================\n\n`;
  }

  const outputPath = path.join(__dirname, '..', '..', '..', 'teacher_schedules.txt');
  fs.writeFileSync(outputPath, output);
  console.log(`Saved to ${outputPath}`);
  process.exit(0);
}

run().catch(console.error);
