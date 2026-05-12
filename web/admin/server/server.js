const express = require('express');
const cors = require('cors');
const path = require('path');
const admin = require('firebase-admin');

// Initialize Firebase Admin SDK
// Supports both local file and environment variable (for cloud deployment)
let serviceAccount;
if (process.env.FIREBASE_SERVICE_ACCOUNT) {
  serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
} else {
  serviceAccount = require('./serviceAccountKey.json');
}

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const auth = admin.auth();
const db = admin.firestore();

const app = express();
app.use(cors());
app.use(express.json());

// Serve the admin frontend
app.use(express.static(path.join(__dirname, '..')));

// ─── CREATE USER ────────────────────────────────────────────
app.post('/api/users', async (req, res) => {
  const { email, password, name, role, section, department } = req.body;

  if (!email || !password || !name || !role) {
    return res.status(400).json({ error: 'email, password, name, role required' });
  }
  if (password.length < 6) {
    return res.status(400).json({ error: 'Password must be at least 6 characters' });
  }

  try {
    // Create in Firebase Auth
    const userRecord = await auth.createUser({
      email,
      password,
      displayName: name,
    });

    // Create Firestore document (matches Android app structure)
    const userData = {
      email: email.toLowerCase(),
      name,
      role,
      isTeacher: role === 'teacher',
      isStudent: role === 'student',
      createdAt: Date.now(),
      createdVia: 'admin_panel',
    };
    if (role === 'student' && section) userData.section = section.toUpperCase();
    if (role === 'teacher' && department) userData.department = department;

    await db.collection('users').doc(userRecord.uid).set(userData);

    res.json({ uid: userRecord.uid, ...userData });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

// ─── UPDATE PASSWORD ────────────────────────────────────────
app.put('/api/users/:uid/password', async (req, res) => {
  const { uid } = req.params;
  const { password } = req.body;

  if (!password || password.length < 6) {
    return res.status(400).json({ error: 'Password must be at least 6 characters' });
  }

  try {
    await auth.updateUser(uid, { password });
    res.json({ message: 'Password updated' });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

// ─── UPDATE USER PROFILE ────────────────────────────────────
app.put('/api/users/:uid', async (req, res) => {
  const { uid } = req.params;
  const { name, role, section, department } = req.body;

  try {
    const updates = {};
    if (name) {
      updates.name = name;
      await auth.updateUser(uid, { displayName: name });
    }
    if (role) {
      updates.role = role;
      updates.isTeacher = role === 'teacher';
      updates.isStudent = role === 'student';
    }
    if (section !== undefined) updates.section = section.toUpperCase();
    if (department !== undefined) updates.department = department;

    await db.collection('users').doc(uid).update(updates);
    res.json({ message: 'User updated' });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

// ─── DELETE USER ────────────────────────────────────────────
app.delete('/api/users/:uid', async (req, res) => {
  const { uid } = req.params;

  try {
    await auth.deleteUser(uid);
    await db.collection('users').doc(uid).delete();
    res.json({ message: 'User deleted' });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

// ─── LIST USERS (from Firestore) ────────────────────────────
app.get('/api/users', async (req, res) => {
  try {
    const snapshot = await db.collection('users').orderBy('createdAt', 'desc').get();
    const users = snapshot.docs.map(doc => ({ uid: doc.id, ...doc.data() }));
    res.json(users);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ─── START ──────────────────────────────────────────────────
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`\n  ┌─────────────────────────────────────────┐`);
  console.log(`  │  Admin Dashboard running on port ${PORT}     │`);
  console.log(`  │  Open: http://localhost:${PORT}            │`);
  console.log(`  └─────────────────────────────────────────┘\n`);
});
