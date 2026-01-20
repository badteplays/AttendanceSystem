import 'dart:convert';

class QRCodeData {
  final String teacherId;
  final String sessionId;
  final String userId;
  final int timestamp;
  final String scheduleId;
  final String subject;
  final String section;
  final int expirationMinutes;
  final double? latitude;
  final double? longitude;
  final int? locationTimestamp;

  static const int defaultExpirationMinutes = 5;

  QRCodeData({
    required this.teacherId,
    required this.sessionId,
    required this.userId,
    required this.timestamp,
    required this.scheduleId,
    required this.subject,
    required this.section,
    this.expirationMinutes = defaultExpirationMinutes,
    this.latitude,
    this.longitude,
    this.locationTimestamp,
  });

  factory QRCodeData.createWithExpiration({
    required String teacherId,
    required String sessionId,
    required String userId,
    required String scheduleId,
    required String subject,
    required String section,
    int expirationMinutes = defaultExpirationMinutes,
    double? latitude,
    double? longitude,
  }) {
    return QRCodeData(
      teacherId: teacherId,
      sessionId: sessionId,
      userId: userId,
      timestamp: DateTime.now().millisecondsSinceEpoch,
      scheduleId: scheduleId,
      subject: subject,
      section: section,
      expirationMinutes: expirationMinutes,
      latitude: latitude,
      longitude: longitude,
      locationTimestamp: (latitude != null && longitude != null)
          ? DateTime.now().millisecondsSinceEpoch
          : null,
    );
  }

  factory QRCodeData.fromJson(String jsonString) {
    try {
      final Map<String, dynamic> data = json.decode(jsonString);
      return QRCodeData(
        teacherId: data['teacherId'] ?? '',
        sessionId: data['sessionId'] ?? '',
        userId: data['userId'] ?? '',
        timestamp: data['timestamp'] ?? 0,
        scheduleId: data['scheduleId'] ?? '',
        subject: data['subject'] ?? '',
        section: data['section'] ?? '',
        expirationMinutes: data['expirationMinutes'] ?? defaultExpirationMinutes,
        latitude: data['latitude']?.toDouble(),
        longitude: data['longitude']?.toDouble(),
        locationTimestamp: data['locationTimestamp'],
      );
    } catch (e) {
      throw ArgumentError('Invalid QR code data format');
    }
  }

  String toJson() {
    return json.encode({
      'teacherId': teacherId,
      'sessionId': sessionId,
      'userId': userId,
      'timestamp': timestamp,
      'scheduleId': scheduleId,
      'subject': subject,
      'section': section,
      'expirationMinutes': expirationMinutes,
      if (latitude != null) 'latitude': latitude,
      if (longitude != null) 'longitude': longitude,
      if (locationTimestamp != null) 'locationTimestamp': locationTimestamp,
    });
  }

  bool get isExpired {
    final expirationTime = timestamp + (expirationMinutes * 60 * 1000);
    return DateTime.now().millisecondsSinceEpoch > expirationTime;
  }

  int get remainingTimeInMillis {
    final expirationTime = timestamp + (expirationMinutes * 60 * 1000);
    final remaining = expirationTime - DateTime.now().millisecondsSinceEpoch;
    return remaining > 0 ? remaining : 0;
  }

  bool get hasLocation => latitude != null && longitude != null;
}
