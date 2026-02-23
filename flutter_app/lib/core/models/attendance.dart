import 'package:cloud_firestore/cloud_firestore.dart';

enum AttendanceStatus { present, absent, late, excused, cutting }

extension AttendanceStatusExtension on AttendanceStatus {
  String get name {
    switch (this) {
      case AttendanceStatus.present:
        return 'PRESENT';
      case AttendanceStatus.absent:
        return 'ABSENT';
      case AttendanceStatus.late:
        return 'LATE';
      case AttendanceStatus.excused:
        return 'EXCUSED';
      case AttendanceStatus.cutting:
        return 'CUTTING';
    }
  }

  static AttendanceStatus fromString(String value) {
    switch (value.toUpperCase()) {
      case 'PRESENT':
        return AttendanceStatus.present;
      case 'ABSENT':
        return AttendanceStatus.absent;
      case 'LATE':
        return AttendanceStatus.late;
      case 'EXCUSED':
        return AttendanceStatus.excused;
      case 'CUTTING':
        return AttendanceStatus.cutting;
      default:
        return AttendanceStatus.present;
    }
  }
}

class Attendance {
  final String id;
  final String studentId;
  final String studentName;
  final Timestamp timestamp;
  final String location;
  final AttendanceStatus status;
  final String notes;
  final String scheduleId;
  final String subject;
  final String section;
  final String sessionId;
  final String teacherId;
  final bool isManualEntry;

  Attendance({
    required this.id,
    required this.studentId,
    required this.studentName,
    required this.timestamp,
    this.location = '',
    this.status = AttendanceStatus.present,
    this.notes = '',
    required this.scheduleId,
    required this.subject,
    required this.section,
    required this.sessionId,
    required this.teacherId,
    this.isManualEntry = false,
  });

  factory Attendance.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return Attendance(
      id: doc.id,
      studentId: data['userId'] ?? '',
      studentName: data['studentName'] ?? 'Unknown Student',
      timestamp: data['timestamp'] ?? Timestamp.now(),
      location: data['location'] ?? '',
      status: AttendanceStatusExtension.fromString(data['status'] ?? 'PRESENT'),
      notes: data['notes'] ?? '',
      scheduleId: data['scheduleId'] ?? '',
      subject: data['subject'] ?? '',
      section: data['section'] ?? '',
      sessionId: data['sessionId'] ?? '',
      teacherId: data['teacherId'] ?? '',
      isManualEntry: data['isManualEntry'] ?? false,
    );
  }

  Map<String, dynamic> toFirestore() {
    return {
      'userId': studentId,
      'studentName': studentName,
      'timestamp': timestamp,
      'location': location,
      'status': status.name,
      'notes': notes,
      'scheduleId': scheduleId,
      'subject': subject,
      'section': section,
      'sessionId': sessionId,
      'teacherId': teacherId,
      'isManualEntry': isManualEntry,
    };
  }

  String get formattedTime {
    final date = timestamp.toDate();
    final hour = date.hour > 12 ? date.hour - 12 : (date.hour == 0 ? 12 : date.hour);
    final period = date.hour >= 12 ? 'PM' : 'AM';
    return '${hour.toString().padLeft(2, '0')}:${date.minute.toString().padLeft(2, '0')} $period';
  }
}
