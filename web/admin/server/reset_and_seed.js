const admin = require('firebase-admin');
let serviceAccount;
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
} else {
  serviceAccount = require('./serviceAccountKey.json');
}
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const auth = admin.auth();
const db = admin.firestore();

// ─── Data Generators ────────────────────────────────────────────────
const rng = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const sample = arr => arr[Math.floor(Math.random() * arr.length)];
const firstNamesM = ["Aaron", "Adrian", "Aiden", "Carlo", "David", "Emilio", "Gabriel", "Ivan", "James", "Leo", "Mark", "Ryan"];
const firstNamesF = ["Abigail", "Bianca", "Camille", "Diana", "Elena", "Isabella", "Jasmine", "Maya", "Olivia", "Sarah"];
const lastNames = ["Cruz", "Santos", "Reyes", "Bautista", "Ocampo", "Aquino", "Garcia", "Mendoza", "Torres", "Flores"];
const strands = ["STEM", "HUMSS", "ABM", "MAWD", "DIGAR"];
const sections = ["301", "302", "303", "304", "305", "401", "402", "403", "404", "405"];
const departments = ["English", "Math", "Science", "Filipino", "PE", "ICT"];
const departmentSubjects = {
  "English": ["EAPP", "ENG12"],
  "Math": ["GENMATH", "STAT"],
  "Science": ["PHYSICS", "CHEM"],
  "Filipino": ["FIL12", "PPTP"],
  "PE": ["PE4", "HOPE4"],
  "ICT": ["SQL", "JAVA"]
};
const activeDays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const times = [
  { s: "06:00", e: "07:00" }, { s: "07:00", e: "08:00" }, { s: "08:00", e: "09:00" },
  { s: "09:00", e: "10:00" }, { s: "10:00", e: "11:00" }, { s: "11:00", e: "12:00" },
  { s: "12:00", e: "13:00" }, { s: "13:00", e: "14:00" }, { s: "14:00", e: "15:00" },
  { s: "15:00", e: "16:00" }, { s: "16:00", e: "17:00" }, { s: "17:00", e: "18:00" },
  { s: "18:00", e: "19:00" }, { s: "19:00", e: "20:00" }
];

let usedEmails = new Set();
const getUniquePerson = (isM) => {
  let first = isM ? sample(firstNamesM) : sample(firstNamesF);
  let last = sample(lastNames);
  let base = `${first.toLowerCase()}.${last.toLowerCase()}`.replace(/\s+/g, '');
  let email = `${base}@school.edu`;
  while (usedEmails.has(email)) {
    email = `${base}${rng(10, 999)}@school.edu`;
  }
  usedEmails.add(email);
  return { name: `${first} ${last}`, email };
};

// ─── Main Routine ───────────────────────────────────────────────────
async function run() {
  console.log("🔥 Wiping Auth Users...");
  let pageToken;
  do {
    const list = await auth.listUsers(1000, pageToken);
    if (list.users.length > 0) {
      await auth.deleteUsers(list.users.map(u => u.uid));
    }
    pageToken = list.pageToken;
  } while (pageToken);

  console.log("🔥 Wiping Firestore Collections...");
  for (const coll of ["users", "schedules"]) {
    const snap = await db.collection(coll).get();
    const batch = db.batch();
    snap.docs.forEach(doc => batch.delete(doc.ref));
    if (snap.size > 0) await batch.commit();
  }

  console.log("✅ Database wiped. Creating accounts...");

  const teachers = [];
  for (let i = 0; i < departments.length; i++) {
    const p = getUniquePerson(rng(0, 1));
    const dept = departments[i];
    const userRec = await auth.createUser({ email: p.email, password: "teacher123", displayName: `Teacher ${p.name}` });
    const data = { email: p.email, name: `Teacher ${p.name}`, role: "teacher", isTeacher: true, isStudent: false, department: dept, createdAt: Date.now() };
    await db.collection("users").doc(userRec.uid).set(data);
    teachers.push({ uid: userRec.uid, department: dept });
    console.log(`👨‍🏫 Teacher created: ${data.name}`);
  }

  let studentCount = 0;
  let batch = db.batch();
  let bCount = 0;
  for (const strand of strands) {
    for (const section of sections) {
      const numStuds = rng(4, 5);
      for (let i = 0; i < numStuds; i++) {
        const p = getUniquePerson(rng(0, 1));
        const userRec = await auth.createUser({ email: p.email, password: "student123", displayName: p.name });
        const data = { email: p.email, name: p.name, role: "student", isTeacher: false, isStudent: true, section: `${strand}-${section}`, createdAt: Date.now() };
        batch.set(db.collection("users").doc(userRec.uid), data);
        studentCount++; bCount++;
        if (bCount === 50) { await batch.commit(); batch = db.batch(); bCount = 0; }
      }
    }
  }
  if (bCount > 0) await batch.commit();
  console.log(`🎓 Created ${studentCount} students.`);

  console.log("📅 Generating schedules...");
  batch = db.batch();
  bCount = 0;
  for (const t of teachers) {
    let days = [...activeDays];
    days.splice(rng(0, days.length - 1), 1); // 1 random day off
    for (const day of days) {
      // 6 to 10 random timeslots per day
      const numClasses = rng(6, 10);
      const dailyTimes = [...times].sort(() => 0.5 - Math.random()).slice(0, numClasses);
      dailyTimes.sort((a, b) => a.s.localeCompare(b.s));
      for (const tSlot of dailyTimes) {
        const strand = sample(strands);
        const section = sample(sections);
        const docRef = db.collection("schedules").doc();
        batch.set(docRef, {
          teacherId: t.uid,
          subject: sample(departmentSubjects[t.department]),
          section: `${strand}-${section}`,
          day: day,
          startTime: tSlot.s,
          endTime: tSlot.e,
          room: `RM-${rng(101, 305)}`,
          lastGeneratedDate: ""
        });
        bCount++;
        if (bCount === 50) { await batch.commit(); batch = db.batch(); bCount = 0; }
      }
    }
  }
  if (bCount > 0) await batch.commit();
  console.log(`✅ Created ${bCount} schedule slots (Total schedules: ~125).`);
  console.log("🎉 ALL DONE!");
  process.exit(0);
}

run().catch(console.error);
