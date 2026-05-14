const admin = require('firebase-admin');
let serviceAccount;
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
} else {
  serviceAccount = require('./serviceAccountKey.json');
}
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

async function run() {
  const usersSnap = await db.collection("users").where("role", "==", "teacher").get();
  const teachers = {};
  usersSnap.forEach(doc => { teachers[doc.id] = doc.data(); });

  const schedSnap = await db.collection("schedules").get();
  const schedulesByTeacher = {};
  
  schedSnap.forEach(doc => {
    const data = doc.data();
    if (!schedulesByTeacher[data.teacherId]) {
      schedulesByTeacher[data.teacherId] = [];
    }
    schedulesByTeacher[data.teacherId].push(data);
  });

  const fs = require('fs');
  let output = "";
  for (const [tId, scheds] of Object.entries(schedulesByTeacher)) {
    const t = teachers[tId] || { name: "Unknown", department: "N/A" };
    output += `\n👨‍🏫 ${t.name} (Dept: ${t.department})\n`;
    output += `--------------------------------------------------\n`;
    
    scheds.forEach(s => {
      output += `  📅 ${s.day} | ${s.startTime} - ${s.endTime} | Room: ${s.room} | Section: ${s.section} | Subject: ${s.subject}\n`;
    });
  }
  fs.writeFileSync('d:/AttendanceSystem/All_Schedules_List.txt', output, 'utf8');
  process.exit(0);
}

run().catch(console.error);
