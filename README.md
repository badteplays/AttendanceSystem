# AttendanceSystem - QR Code Based Attendance Management

A modern **cross-platform** attendance tracking application for educational institutions using QR code technology. Available for both **Android** and **iOS** with a beautiful, unified experience.

## 📱 Overview

**AttendanceSystem** is a comprehensive attendance management solution that leverages QR code technology to enable quick and contactless attendance marking. The app provides separate interfaces for teachers and students, with real-time synchronization through Firebase.

### 🆕 Now Available in Flutter!

The app has been completely rebuilt in **Flutter** for cross-platform support:
- ✅ **Android** - Native performance
- ✅ **iOS** - Full iPhone/iPad support
- ✅ **Same Firebase backend** - All data syncs across platforms
- ✅ **Modern UI** - Beautiful gradient design with animations

## ✨ Key Features

### 👨‍🎓 For Students

- **QR Code Scanner**
  - Fast and accurate QR code scanning for marking attendance
  - Real-time validation with duplicate prevention
  - Visual and haptic feedback on successful scan
  - Automatic timer display showing remaining time to scan

- **Dashboard**
  - Current class status display with animated statistics
  - Quick access to QR scanner with gradient FAB
  - Monthly attendance breakdown (Present, Absent, Late)
  - Real-time attendance status updates

- **Class Schedule Management**
  - View complete weekly schedule with timeline view
  - See all enrolled classes by section
  - Color-coded active class indicators
  - Display class details (subject, room, time)

- **Attendance History**
  - View complete attendance records with animations
  - Status-colored cards (green/amber/red)
  - See attendance status (Present, Excused, Late, Absent)
  - Track attendance percentage

- **Profile Management**
  - Beautiful gradient profile cards
  - View section and enrollment details
  - Manage notification preferences
  - Sign out with confirmation

### 👨‍🏫 For Teachers

- **QR Code Generation**
  - Generate unique QR codes with glowing animations
  - Configurable expiration (5-60 minutes)
  - Live countdown timer display
  - Regenerate codes instantly

- **Real-Time Attendance Dashboard**
  - Live attendance updates as students scan
  - See student names, sections, and scan times
  - Current class information display
  - "LIVE" indicator with pulsing animation

- **Manual Attendance Management**
  - Manually add students who forgot to scan
  - Beautiful dialog with gradient buttons
  - Search and select students by section

- **Schedule Management**
  - Create and manage class schedules
  - View schedules grouped by day
  - Color-coded schedule cards
  - "TODAY" indicator badge

- **Analytics & Reporting**
  - Animated percentage counters
  - Monthly attendance overview card
  - Breakdown by status (Present, Late, Absent, Excused)
  - Beautiful gradient stat cards

## 🎨 Design Features

- **Modern Dark Theme** - Deep blacks with layered surfaces
- **Gradient Accents** - Indigo → Violet → Pink color scheme
- **Glass Morphism** - Subtle bordered containers
- **Micro-animations** - Counters, transitions, and pulses
- **Space Grotesk Font** - Clean, modern typography

## 🔧 Technologies Used

### Flutter App (Cross-Platform)
- **Framework**: Flutter 3.24+
- **Language**: Dart
- **State Management**: Provider
- **Backend**: Firebase
  - Firebase Authentication
  - Cloud Firestore
  - Firebase Messaging
- **QR Code**: qr_flutter & mobile_scanner
- **UI**: Custom widgets with gradients

### Android App (Legacy)
- **Language**: Kotlin
- **Architecture**: MVVM with Fragments
- **QR Code**: ZXing library
- **Background Tasks**: WorkManager
- **UI Components**: Material Design 3

## 📋 Prerequisites

### For Flutter App

1. **Flutter SDK** (3.10.0 or later)
   - Download: [Flutter Official Website](https://docs.flutter.dev/get-started/install)

2. **IDE** (Choose one)
   - Android Studio with Flutter plugin
   - VS Code with Flutter extension

3. **For iOS Development** (Mac only)
   - Xcode 14.0 or later
   - CocoaPods

4. **Firebase Project**
   - Firebase Authentication enabled
   - Cloud Firestore database

### For Android App (Legacy)

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK 24-35

## 🚀 Installation

### Flutter App Setup

```bash
# Clone the repository
git clone https://github.com/badteplays/AttendanceSystem.git
cd AttendanceSystem/flutter_app

# Install dependencies
flutter pub get

# Copy Firebase config
# Android: Copy google-services.json to android/app/
# iOS: Copy GoogleService-Info.plist to ios/Runner/

# Run the app
flutter run
```

### Configure Firebase

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create or select your project
3. Add Android app with package: `com.example.attendance_system`
4. Add iOS app with bundle ID: `com.example.attendanceSystem`
5. Download config files and place in appropriate directories
6. Enable Authentication (Email/Password)
7. Create Firestore Database

### Generate App Icons

```bash
cd flutter_app
flutter pub get
dart run flutter_launcher_icons
```

## 📊 Database Structure

### Collections:
- `users` - User profiles (students and teachers)
- `schedules` - Class schedules with time and section info
- `attendance` - Active attendance records
- `archived_attendance` - Historical attendance data
- `attendance_sessions` - QR code session management

## 🔐 User Roles

### Student
- Mark attendance by scanning QR codes
- View personal schedule and attendance history
- Track attendance statistics
- Receive class reminders

### Teacher
- Generate QR codes for attendance
- Monitor real-time attendance
- Manage class schedules
- Add/remove attendance manually
- View analytics and reports

## 📱 App Structure

```
flutter_app/
├── lib/
│   ├── core/
│   │   ├── models/          # Data models
│   │   ├── services/        # Firebase services
│   │   ├── theme/           # Colors & theme
│   │   └── widgets/         # Reusable widgets
│   ├── features/
│   │   ├── auth/            # Login, Signup, Role selection
│   │   ├── student/         # Student screens
│   │   ├── teacher/         # Teacher screens
│   │   └── qr/              # QR Scanner & Display
│   └── main.dart
├── assets/
│   ├── icon/                # App icons
│   └── images/              # Image assets
└── pubspec.yaml
```

## 🐛 Known Issues

- Teacher recent attendance recycler optimization in progress
- Student status auto-update after class ends being improved

## 🔗 Repository

[GitHub Repository](https://github.com/badteplays/AttendanceSystem)

---

**Version**: 2.0 (Flutter)  
**Last Updated**: January 2026  
**Platforms**: Android, iOS  
**Min Android SDK**: 21 (Android 5.0)  
**Min iOS**: 12.0
