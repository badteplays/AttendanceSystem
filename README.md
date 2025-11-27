# AttendanceSystem - QR Code Based Attendance Management

A modern Android application designed to streamline attendance tracking for educational institutions using QR code technology. This app eliminates the traditional paper-based attendance method, making the process faster, more accurate, and efficient for both teachers and students.

## 📱 Overview

**AttendanceSystem** is a comprehensive attendance management solution that leverages QR code technology to enable quick and contactless attendance marking. The app provides separate interfaces for teachers and students, with real-time synchronization through Firebase.

## ✨ Key Features

### 👨‍🎓 For Students

- **QR Code Scanner**
  - Fast and accurate QR code scanning for marking attendance
  - Real-time validation with duplicate prevention
  - Visual and haptic feedback on successful scan
  - Automatic timer display showing remaining time to scan

- **Dashboard**
  - Current class status display
  - Quick access to QR scanner
  - View next scheduled class
  - Real-time attendance status updates

- **Class Schedule Management**
  - View complete weekly schedule
  - See all enrolled classes by section
  - Filter by day and time
  - Display class details (subject, section, time)

- **Personal Routines**
  - Create custom study/activity routines
  - Schedule management with conflict detection
  - View and manage daily routines
  - Automatic validation against class schedule

- **Attendance History**
  - View complete attendance records
  - Filter by date and subject
  - See attendance status (Present, Excused, Cutting)
  - Track attendance percentage

- **Class Reminders**
  - Configurable notification timing (5-60 minutes before class)
  - Automatic background notifications for upcoming classes
  - Enable/disable reminder system
  - Works even when app is closed

- **Profile Management**
  - Update profile picture
  - View section and enrollment details
  - Theme customization (Light/Dark/System)
  - Manage notification preferences

### 👨‍🏫 For Teachers

- **QR Code Generation**
  - Generate unique QR codes for each class session
  - Time-limited QR codes with automatic expiration
  - Renew QR codes during class
  - Display QR code in full-screen mode

- **Real-Time Attendance Dashboard**
  - Live attendance updates as students scan
  - See student names, sections, and scan times
  - Current class information display
  - Total attendance count

- **Manual Attendance Management**
  - Manually add students who forgot to scan
  - Mark attendance status (Present, Excused, Cutting)
  - Remove incorrect attendance entries
  - Search and select students by section

- **Schedule Management**
  - Create and manage class schedules
  - Set recurring weekly schedules
  - Edit or delete existing schedules
  - Multiple sections support

- **Analytics & Reporting**
  - View attendance statistics by subject
  - Filter by date range and section
  - See individual student attendance records
  - Export and analyze attendance data

- **Session Management**
  - Automatic session archiving after class ends
  - End class manually to archive attendance
  - View archived attendance history
  - Separate active and archived records

- **Profile & Settings**
  - Update profile information
  - Manage department details
  - Customize app theme
  - Configure notification preferences

## 🔧 Technologies Used

- **Language**: Kotlin
- **Architecture**: MVVM with Fragments
- **Backend**: Firebase
  - Firebase Authentication
  - Cloud Firestore
  - Firebase Cloud Messaging
- **QR Code**: ZXing (Zebra Crossing) library
- **Background Tasks**: WorkManager
- **UI Components**: Material Design 3
- **Image Loading**: Glide
- **Permissions**: RuntimePermissions API

## 🚀 Installation

1. Clone the repository:
```bash
git clone https://github.com/badteplays/AttendanceSystem.git
```

2. Open the project in Android Studio

3. Configure Firebase:
   - Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
   - Add an Android app to your Firebase project
   - Download `google-services.json` and place it in the `app/` directory
   - Enable Firebase Authentication (Email/Password)
   - Set up Cloud Firestore database

4. Build and run the app on your device or emulator

## 📋 Permissions Required

The app automatically requests the following permissions:

- **Camera**: Required for scanning QR codes (students) and displaying QR codes (teachers)
- **Notifications** (Android 13+): Optional, for class reminder notifications
- **Vibration**: For haptic feedback on successful scan

## 🔐 User Roles

### Student
- Mark attendance by scanning QR codes
- View personal schedule and attendance history
- Manage routines and receive class reminders
- Track attendance percentage

### Teacher
- Generate QR codes for attendance
- Monitor real-time attendance
- Manage class schedules
- Add/remove attendance manually
- View analytics and reports

## 📊 Database Structure

### Collections:
- `users` - User profiles (students and teachers)
- `schedules` - Class schedules with time and section info
- `attendance` - Active attendance records
- `archived_attendance` - Historical attendance data
- `attendance_sessions` - QR code session management
- `routines` - Student personal routines

## 🎨 Features Highlights

- **Real-time Updates**: Live attendance synchronization using Firestore listeners
- **Offline Support**: Basic functionality works offline with automatic sync
- **Material Design**: Modern and intuitive user interface
- **Dark Mode**: System-wide theme support
- **Security**: Session-based QR codes with expiration and duplicate prevention
- **Scalability**: Designed to handle multiple classes and sections simultaneously
- **Performance**: Optimized queries and efficient data handling

## 🐛 Bug Fixes & Improvements

Recent updates include:
- Fixed live attendance updates for teachers
- Improved QR scanner state management
- Added backward compatibility for section data
- Enhanced notification system for Android 13+
- Fixed race conditions in login flow
- Improved permission request handling

## 👥 Contributors

Developed for efficient attendance management in educational institutions.

## 📄 License

This project is developed as an educational attendance management system.

## 🔗 Repository

[GitHub Repository](https://github.com/badteplays/AttendanceSystem)

---

**Version**: 1.0  
**Last Updated**: November 2024  
**Min SDK**: 24 (Android 7.0)  
**Target SDK**: 35 (Android 15)
