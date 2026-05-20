const { db, admin } = require('../lib/firebase');

module.exports = async function handler(req, res) {
  // ─── GET /api/schedules — list schedules ──────────────────────
  if (req.method === 'GET') {
    const { teacherId } = req.query;
    if (!teacherId) {
      return res.status(400).json({ error: 'teacherId required' });
    }

    try {
      const snapshot = await db.collection('schedules')
        .where('teacherId', '==', teacherId)
        .get();
      
      const schedules = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      return res.status(200).json(schedules);
    } catch (e) {
      return res.status(500).json({ error: e.message });
    }
  }

  // ─── POST /api/schedules — create schedule ────────────────────
  if (req.method === 'POST') {
    const { teacherId, subject, section, day, startTime, endTime, room } = req.body;

    if (!teacherId || !subject || !section || !day || !startTime || !endTime) {
      return res.status(400).json({ error: 'Missing required fields' });
    }

    try {
      // Fetch teacher info to include teacherName and department
      const teacherDoc = await db.collection('users').doc(teacherId).get();
      if (!teacherDoc.exists) {
        return res.status(400).json({ error: 'Teacher user not found' });
      }
      const teacherData = teacherDoc.data();

      const schedDoc = {
        teacherId,
        teacherName: teacherData.name || 'Unknown',
        department: teacherData.department || 'General',
        subject,
        section: section.toUpperCase(),
        day,
        startTime,
        endTime,
        room: room || '',
        status: 'scheduled',
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      };

      const docRef = await db.collection('schedules').add(schedDoc);
      return res.status(200).json({ id: docRef.id, ...schedDoc });
    } catch (e) {
      return res.status(400).json({ error: e.message });
    }
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
