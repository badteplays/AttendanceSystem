import 'package:cloud_firestore/cloud_firestore.dart';
import '../models/attendance.dart';
import '../models/schedule.dart';
import '../models/user_model.dart';

class FirestoreService {
  final FirebaseFirestore _firestore = FirebaseFirestore.instance;

  Future<Map<String, dynamic>?> getUserData(String uid) async {
    final doc = await _firestore.collection('users').doc(uid).get();
    return doc.exists ? doc.data() : null;
  }

  Future<void> createUserData({
    required String uid,
    required String name,
    required String email,
    required bool isTeacher,
    required bool isStudent,
    String? section,
    String? department,
  }) async {
    await _firestore.collection('users').doc(uid).set({
      'name': name,
      'email': email,
      'isTeacher': isTeacher,
      'isStudent': isStudent,
      if (section != null) 'section': section,
      if (department != null) 'department': department,
      'createdAt': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Stream<DocumentSnapshot<Map<String, dynamic>>> getUserStream(String uid) {
    return _firestore.collection('users').doc(uid).snapshots();
  }

  Future<List<Schedule>> getTeacherSchedules(String teacherId) async {
    final snapshot = await _firestore
        .collection('schedules')
        .where('teacherId', isEqualTo: teacherId)
        .get();
    return snapshot.docs.map((doc) => Schedule.fromFirestore(doc)).toList();
  }

  Future<List<Schedule>> getSchedulesForSection(String section, String day) async {
    final snapshot = await _firestore
        .collection('schedules')
        .where('section', isEqualTo: section)
        .where('day', isEqualTo: day)
        .get();
    return snapshot.docs.map((doc) => Schedule.fromFirestore(doc)).toList();
  }

  Future<List<Schedule>> getTodaySchedulesForTeacher(String teacherId, String day) async {
    final snapshot = await _firestore
        .collection('schedules')
        .where('teacherId', isEqualTo: teacherId)
        .where('day', isEqualTo: day)
        .get();
    return snapshot.docs.map((doc) => Schedule.fromFirestore(doc)).toList();
  }

  Future<List<Schedule>> getTodaySchedulesForSection(String section, String day) async {
    final snapshot = await _firestore
        .collection('schedules')
        .where('section', isEqualTo: section)
        .where('day', isEqualTo: day)
        .get();
    return snapshot.docs.map((doc) => Schedule.fromFirestore(doc)).toList();
  }

  Stream<QuerySnapshot<Map<String, dynamic>>> getAttendanceStream({
    required String teacherId,
    required String scheduleId,
    required String subject,
    required Timestamp fromTimestamp,
  }) {
    return _firestore
        .collection('attendance')
        .where('teacherId', isEqualTo: teacherId)
        .where('scheduleId', isEqualTo: scheduleId)
        .where('subject', isEqualTo: subject)
        .where('timestamp', isGreaterThanOrEqualTo: fromTimestamp)
        .snapshots();
  }

  Future<List<Attendance>> getStudentAttendanceHistory(String userId) async {
    final snapshot = await _firestore
        .collection('attendance')
        .where('userId', isEqualTo: userId)
        .orderBy('timestamp', descending: true)
        .limit(100)
        .get();
    return snapshot.docs.map((doc) => Attendance.fromFirestore(doc)).toList();
  }

  Future<Map<String, int>> getMonthlyAttendanceStats(String userId) async {
    final now = DateTime.now();
    final monthStart = DateTime(now.year, now.month, 1);
    final monthStartTimestamp = Timestamp.fromDate(monthStart);

    final snapshot = await _firestore
        .collection('attendance')
        .where('userId', isEqualTo: userId)
        .where('timestamp', isGreaterThanOrEqualTo: monthStartTimestamp)
        .get();

    int presentCount = 0;
    int absentCount = 0;
    int lateCount = 0;

    for (final doc in snapshot.docs) {
      final status = (doc.data()['status'] as String?)?.toUpperCase() ?? 'PRESENT';
      switch (status) {
        case 'PRESENT':
          presentCount++;
          break;
        case 'ABSENT':
          absentCount++;
          break;
        case 'LATE':
          lateCount++;
          break;
        case 'EXCUSED':
          presentCount++;
          break;
        case 'CUTTING':
          absentCount++;
          break;
      }
    }

    return {
      'present': presentCount,
      'absent': absentCount,
      'late': lateCount,
    };
  }

  Future<String> markAttendance({
    required String userId,
    required String studentName,
    required String sessionId,
    required String teacherId,
    required String scheduleId,
    required String subject,
    required String section,
    AttendanceStatus status = AttendanceStatus.present,
  }) async {
    final docRef = await _firestore.collection('attendance').add({
      'userId': userId,
      'studentName': studentName,
      'sessionId': sessionId,
      'teacherId': teacherId,
      'scheduleId': scheduleId,
      'subject': subject,
      'section': section,
      'timestamp': Timestamp.now(),
      'status': status.name,
      'location': '',
      'notes': '',
    });
    return docRef.id;
  }

  Future<bool> hasAlreadyMarkedAttendance(String userId, String sessionId) async {
    final snapshot = await _firestore
        .collection('attendance')
        .where('userId', isEqualTo: userId)
        .where('sessionId', isEqualTo: sessionId)
        .get();
    return snapshot.docs.isNotEmpty;
  }

  Future<void> deleteAttendance(String attendanceId) async {
    await _firestore.collection('attendance').doc(attendanceId).delete();
  }

  Future<void> archiveAttendance(String attendanceId) async {
    final doc = await _firestore.collection('attendance').doc(attendanceId).get();
    if (doc.exists) {
      final data = doc.data()!;
      data['archivedAt'] = DateTime.now().millisecondsSinceEpoch;
      data['originalId'] = attendanceId;
      await _firestore.collection('archived_attendance').doc(attendanceId).set(data);
      await _firestore.collection('attendance').doc(attendanceId).delete();
    }
  }

  Future<Map<String, dynamic>?> getActiveSession(String scheduleId, String teacherId) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final snapshot = await _firestore
        .collection('attendance_sessions')
        .where('scheduleId', isEqualTo: scheduleId)
        .where('teacherId', isEqualTo: teacherId)
        .get();

    for (final doc in snapshot.docs) {
      final expiresAt = doc.data()['expiresAt'] as int? ?? 0;
      if (expiresAt > now) {
        return {'id': doc.id, ...doc.data()};
      }
    }
    return null;
  }

  Future<String> createSession({
    required String sessionId,
    required String teacherId,
    required String scheduleId,
    required String subject,
    required String section,
    required int expirationMinutes,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await _firestore.collection('attendance_sessions').doc(sessionId).set({
      'sessionId': sessionId,
      'teacherId': teacherId,
      'scheduleId': scheduleId,
      'subject': subject,
      'section': section,
      'createdAt': now,
      'expiresAt': now + (expirationMinutes * 60 * 1000),
    });
    return sessionId;
  }

  Future<void> deleteOldSessions(String scheduleId, String teacherId) async {
    final snapshot = await _firestore
        .collection('attendance_sessions')
        .where('scheduleId', isEqualTo: scheduleId)
        .where('teacherId', isEqualTo: teacherId)
        .get();

    for (final doc in snapshot.docs) {
      await doc.reference.delete();
    }
  }

  Future<Map<String, dynamic>?> validateSession(String sessionId) async {
    final doc = await _firestore.collection('attendance_sessions').doc(sessionId).get();
    if (!doc.exists) return null;
    return doc.data();
  }

  Future<List<UserModel>> getStudentsInSection(String section) async {
    final snapshot = await _firestore
        .collection('users')
        .where('isStudent', isEqualTo: true)
        .get();

    return snapshot.docs
        .where((doc) => (doc.data()['section'] as String?)?.toLowerCase() == section.toLowerCase())
        .map((doc) => UserModel.fromFirestore(doc))
        .toList();
  }

  Future<void> addManualAttendance({
    required String teacherId,
    required String studentName,
    String? studentId,
    required String scheduleId,
    required String subject,
    required String section,
  }) async {
    await _firestore.collection('attendance').add({
      'userId': studentId ?? 'MANUAL_${DateTime.now().millisecondsSinceEpoch}',
      'studentName': studentName,
      'sessionId': 'MANUAL_${DateTime.now().millisecondsSinceEpoch}',
      'teacherId': teacherId,
      'scheduleId': scheduleId,
      'subject': subject,
      'section': section,
      'timestamp': Timestamp.now(),
      'status': 'PRESENT',
      'location': '',
      'notes': 'Manually added by teacher',
      'isManualEntry': true,
    });
  }

  Future<void> addSchedule(Schedule schedule) async {
    await _firestore.collection('schedules').add(schedule.toFirestore());
  }

  Future<void> updateSchedule(String scheduleId, Map<String, dynamic> data) async {
    await _firestore.collection('schedules').doc(scheduleId).update(data);
  }

  Future<void> deleteSchedule(String scheduleId) async {
    await _firestore.collection('schedules').doc(scheduleId).delete();
  }
}
