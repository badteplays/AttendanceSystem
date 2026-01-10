import 'package:cloud_firestore/cloud_firestore.dart';

class UserModel {
  final String id;
  final String email;
  final String name;
  final String section;
  final String department;
  final bool isTeacher;
  final bool isStudent;
  final String? profilePicUrl;
  final String role;

  UserModel({
    required this.id,
    required this.email,
    this.name = '',
    this.section = '',
    this.department = '',
    this.isTeacher = false,
    this.isStudent = true,
    this.profilePicUrl,
    this.role = 'student',
  });

  factory UserModel.fromFirestore(DocumentSnapshot doc) {
    final data = doc.data() as Map<String, dynamic>;
    return UserModel(
      id: doc.id,
      email: data['email'] ?? '',
      name: data['name'] ?? '',
      section: data['section'] ?? '',
      department: data['department'] ?? '',
      isTeacher: data['isTeacher'] ?? false,
      isStudent: data['isStudent'] ?? true,
      profilePicUrl: data['profilePicUrl'],
      role: data['role'] ?? 'student',
    );
  }

  Map<String, dynamic> toFirestore() {
    return {
      'email': email,
      'name': name,
      'section': section,
      'department': department,
      'isTeacher': isTeacher,
      'isStudent': isStudent,
      if (profilePicUrl != null) 'profilePicUrl': profilePicUrl,
      'role': role,
    };
  }

  String get initials {
    if (name.isEmpty) return isTeacher ? 'TC' : 'ST';
    final parts = name.trim().split(' ');
    if (parts.length >= 2) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return name.substring(0, name.length > 1 ? 2 : 1).toUpperCase();
  }

  UserModel copyWith({
    String? id,
    String? email,
    String? name,
    String? section,
    String? department,
    bool? isTeacher,
    bool? isStudent,
    String? profilePicUrl,
    String? role,
  }) {
    return UserModel(
      id: id ?? this.id,
      email: email ?? this.email,
      name: name ?? this.name,
      section: section ?? this.section,
      department: department ?? this.department,
      isTeacher: isTeacher ?? this.isTeacher,
      isStudent: isStudent ?? this.isStudent,
      profilePicUrl: profilePicUrl ?? this.profilePicUrl,
      role: role ?? this.role,
    );
  }
}
