const { db } = require('../../lib/firebase');

module.exports = async function handler(req, res) {
  const { id } = req.query;

  // ─── PUT /api/schedules/:id — update schedule ────────────────
  if (req.method === 'PUT') {
    const { subject, section, day, startTime, endTime, room } = req.body;

    try {
      const updates = {};
      if (subject !== undefined) updates.subject = subject;
      if (section !== undefined) updates.section = section.toUpperCase();
      if (day !== undefined) updates.day = day;
      if (startTime !== undefined) updates.startTime = startTime;
      if (endTime !== undefined) updates.endTime = endTime;
      if (room !== undefined) updates.room = room;

      await db.collection('schedules').doc(id).update(updates);
      return res.status(200).json({ message: 'Schedule updated' });
    } catch (e) {
      return res.status(400).json({ error: e.message });
    }
  }

  // ─── DELETE /api/schedules/:id — delete schedule ─────────────
  if (req.method === 'DELETE') {
    try {
      await db.collection('schedules').doc(id).delete();
      return res.status(200).json({ message: 'Schedule deleted' });
    } catch (e) {
      return res.status(400).json({ error: e.message });
    }
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
