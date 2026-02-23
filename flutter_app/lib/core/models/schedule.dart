import 'package:cloud_firestore/cloud_firestore.dart';

class Schedule {
  final String id;
  final String subject;
  final String section;
  final String teacherId;
  final String startTime;
  final String endTime;
  final String day;
  final String room;
  final String lastGeneratedDate;

  Schedule({
    required this.id,
    required this.subject,
    required this.section,
    required this.teacherId,
    required this.startTime,
    required this.endTime,
    required this.day,
    this.room = '',
    this.lastGeneratedDate = '',
  });

  factory Schedule.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return Schedule(
      id: doc.id,
      subject: data['subject'] ?? '',
      section: data['section'] ?? '',
      teacherId: data['teacherId'] ?? '',
      startTime: data['startTime'] ?? '',
      endTime: data['endTime'] ?? '',
      day: data['day'] ?? '',
      room: data['room'] ?? '',
      lastGeneratedDate: data['lastGeneratedDate'] ?? '',
    );
  }

  Map<String, dynamic> toFirestore() {
    return {
      'subject': subject,
      'section': section,
      'teacherId': teacherId,
      'startTime': startTime,
      'endTime': endTime,
      'day': day,
      'room': room,
      'lastGeneratedDate': lastGeneratedDate,
    };
  }

  int get startTimeInMinutes => _parseTimeToMinutes(startTime);
  int get endTimeInMinutes => _parseTimeToMinutes(endTime);

  int _parseTimeToMinutes(String time) {
    try {
      final parts = time.split(':');
      final hour = int.parse(parts[0]);
      final minute = int.parse(parts[1]);
      return hour * 60 + minute;
    } catch (e) {
      return 0;
    }
  }

  bool isCurrentlyActive(int nowMinutes) {
    final start = startTimeInMinutes;
    final end = endTimeInMinutes;
    if (end < start) {
      return nowMinutes >= start || nowMinutes <= end;
    }
    return nowMinutes >= start && nowMinutes <= end;
  }

  String get formattedStartTime => _formatTime(startTime);
  String get formattedEndTime => _formatTime(endTime);

  String _formatTime(String time) {
    try {
      final parts = time.split(':');
      int hour = int.parse(parts[0]);
      final minute = int.parse(parts[1]);
      final period = hour >= 12 ? 'PM' : 'AM';
      hour = hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour);
      return '${hour.toString()}:${minute.toString().padLeft(2, '0')} $period';
    } catch (e) {
      return time;
    }
  }
}
