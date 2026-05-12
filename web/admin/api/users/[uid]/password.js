const { auth } = require('../../lib/firebase');

module.exports = async function handler(req, res) {
  if (req.method !== 'PUT') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  const { uid } = req.query;
  const { password } = req.body;

  if (!password || password.length < 6) {
    return res.status(400).json({ error: 'Password must be at least 6 characters' });
  }

  try {
    await auth.updateUser(uid, { password });
    return res.status(200).json({ message: 'Password updated' });
  } catch (e) {
    return res.status(400).json({ error: e.message });
  }
};
