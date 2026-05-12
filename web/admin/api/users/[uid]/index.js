const { auth, db } = require('../../lib/firebase');

module.exports = async function handler(req, res) {
  const { uid } = req.query;

  // ─── PUT /api/users/:uid — update profile ─────────────────
  if (req.method === 'PUT') {
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
      return res.status(200).json({ message: 'User updated' });
    } catch (e) {
      return res.status(400).json({ error: e.message });
    }
  }

  // ─── DELETE /api/users/:uid — delete user ─────────────────
  if (req.method === 'DELETE') {
    try {
      await auth.deleteUser(uid);
      await db.collection('users').doc(uid).delete();
      return res.status(200).json({ message: 'User deleted' });
    } catch (e) {
      return res.status(400).json({ error: e.message });
    }
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
