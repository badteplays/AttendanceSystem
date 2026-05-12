const { auth, db } = require('../lib/firebase');

module.exports = async function handler(req, res) {
  // ─── GET /api/users — list all users ──────────────────────
  if (req.method === 'GET') {
    try {
      const snapshot = await db.collection('users').orderBy('createdAt', 'desc').get();
      const users = snapshot.docs.map(doc => ({ uid: doc.id, ...doc.data() }));
      return res.status(200).json(users);
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ─── POST /api/users — create user ────────────────────────
  if (req.method === 'POST') {
    const { email, password, name, role, section, department } = req.body;

    if (!email || !password || !name || !role) {
      return res.status(400).json({ error: 'email, password, name, role required' });
    }
    if (password.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters' });
    }

    try {
      const userRecord = await auth.createUser({ email, password, displayName: name });

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
      return res.status(200).json({ uid: userRecord.uid, ...userData });
    } catch (e) {
      return res.status(400).json({ error: e.message });
    }
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
