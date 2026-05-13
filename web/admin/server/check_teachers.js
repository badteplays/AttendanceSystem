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
  const tSnap = await db.collection("users").where("role", "==", "teacher").get();
  console.log(`Found ${tSnap.docs.length} teachers in Firestore.`);

  const allSnap = await db.collection("users").get();
  console.log(`Found ${allSnap.docs.length} total users in Firestore.`);
}

run().catch(console.error);
