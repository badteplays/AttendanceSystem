const admin = require('firebase-admin');
let serviceAccount;
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
} else {
  serviceAccount = require('./serviceAccountKey.json');
}
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

const rng = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const sample = arr => arr[Math.floor(Math.random() * arr.length)];
const strands = ["STEM", "HUMSS", "ABM", "MAWD", "DIGAR"];
const sections = ["101", "102", "103", "104", "105", "201", "202", "203", "204", "205"];
const activeDays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

// 6 AM to 8 PM (14 slots)
const allTimes = [
  {s: "06:00", e: "07:00"}, {s: "07:00", e: "08:00"}, {s: "08:00", e: "09:00"},
  {s: "09:00", e: "10:00"}, {s: "10:00", e: "11:00"}, {s: "11:00", e: "12:00"},
  {s: "12:00", e: "13:00"}, {s: "13:00", e: "14:00"}, {s: "14:00", e: "15:00"},
  {s: "15:00", e: "16:00"}, {s: "16:00", e: "17:00"}, {s: "17:00", e: "18:00"},
  {s: "18:00", e: "19:00"}, {s: "19:00", e: "20:00"}
];

async function run() {
  console.log("🔥 Wiping old schedules...");
  const snap = await db.collection("schedules").get();
  let batch = db.batch();
  let bCount = 0;
  for (const doc of snap.docs) {
    batch.delete(doc.ref);
    bCount++;
    if (bCount === 400) { await batch.commit(); batch = db.batch(); bCount = 0; }
  }
  if (bCount > 0) await batch.commit();

  console.log("👨‍🏫 Fetching teachers...");
  const teachersSnap = await db.collection("users").where("role", "==", "teacher").get();
  const teachers = teachersSnap.docs.map(d => ({ uid: d.id, ...d.data() }));
  console.log(`Found ${teachers.length} teachers.`);

  console.log("📅 Generating new schedules (6am-8pm with random breaks)...");
  batch = db.batch();
  bCount = 0;
  let totalGenerated = 0;

  for (const t of teachers) {
    // each teacher works some random days
    let days = [...activeDays];
    days.splice(rng(0, days.length - 1), rng(1, 2)); // 1-2 random days off
    
    for (const day of days) {
      // Out of 14 slots, maybe they get 6 to 10 classes, rest are breaks
      const numClasses = rng(6, 10);
      const dailyTimes = [...allTimes].sort(() => 0.5 - Math.random()).slice(0, numClasses);
      
      // Sort them chronologically just in case
      dailyTimes.sort((a, b) => a.s.localeCompare(b.s));

      for (const tSlot of dailyTimes) {
        const strand = sample(strands);
        const section = sample(sections);
        const docRef = db.collection("schedules").doc();
        batch.set(docRef, {
          teacherId: t.uid,
          subject: t.department || "General",
          section: `${strand}-${section}`,
          day: day,
          startTime: tSlot.s,
          endTime: tSlot.e,
          room: `RM-${rng(101, 305)}`,
          lastGeneratedDate: ""
        });
        bCount++;
        totalGenerated++;
        if (bCount === 400) { await batch.commit(); batch = db.batch(); bCount = 0; }
      }
    }
  }
  if (bCount > 0) await batch.commit();
  console.log(`✅ Created ${totalGenerated} schedule slots.`);
  console.log("🎉 ALL DONE!");
  process.exit(0);
}

run().catch(console.error);
