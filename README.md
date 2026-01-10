# AttendanceSystem - Cross-Platform QR Code Attendance Management

A modern **cross-platform** application for Android and iOS, designed to streamline attendance tracking for educational institutions using QR code technology. Built with Flutter for seamless experience across all devices.

## 📱 Overview

**AttendanceSystem** is a comprehensive attendance management solution that leverages QR code technology to enable quick and contactless attendance marking. The app provides separate interfaces for teachers and students, with real-time synchronization through Firebase.

### 🎯 Available Platforms

| Platform | Status | Technology |
|----------|--------|------------|
| Android | ✅ Available | Flutter / Native Kotlin |
| iOS | ✅ Available | Flutter |

## ✨ Key Features

### 👨‍🎓 For Students

- **QR Code Scanner**
  - Fast and accurate QR code scanning for marking attendance
  - Real-time validation with duplicate prevention
  - Visual and haptic feedback on successful scan
  - Automatic timer display showing remaining time to scan

- **Dashboard**
  - Animated statistics with live counters
  - Quick access to QR scanner with gradient FAB
  - Today's attendance status display
  - Monthly attendance breakdown (Present/Absent/Late)

- **Class Schedule Management**
  - Beautiful timeline view with day selector
  - See all enrolled classes by section
  - Active class highlighting
  - Display class details (subject, room, time)

- **Attendance History**
  - Animated list with staggered animations
  - Status-colored cards (green/amber/red)
  - Filter by date and subject
  - Track attendance percentage

- **Profile & Settings**
  - Gradient profile card
  - Theme customization
  - Notification preferences
  - Sign out functionality

### 👨‍🏫 For Teachers

- **QR Code Generation**
  - Generate unique QR codes with animated glow effect
  - Configurable expiration (5-60 minutes)
  - Live countdown timer with progress indicator
  - One-tap regeneration

- **Real-Time Attendance Dashboard**
  - Live attendance updates as students scan
  - Animated student list with avatars
  - Current class information display
  - Total attendance count with "LIVE" indicator

- **Manual Attendance Management**
  - Beautiful modal dialog for adding students
  - Search and select students by section
  - Mark attendance status

- **Schedule Management**
  - Visual timeline for all classes
  - Color-coded by subject
  - "Today" indicator badge
  - Add new classes (coming soon)

- **Analytics & Reporting**
  - Animated percentage counters
  - Monthly attendance overview card
  - Status breakdown with gradient icons
  - Total records tracking

## 🎨 UI/UX Design

The app features a **stunning modern dark theme** with:

- **Gradient Color Scheme**: Indigo (#6366F1) → Violet (#8B5CF6) → Pink (#EC4899)
- **Glass Morphism Cards**: Subtle borders with depth
- **Smooth Animations**: Staggered list animations, counter animations, pulse effects
- **Custom Bottom Navigation**: Expanding items with gradient backgrounds
- **Typography**: Space Grotesk font family
- **Status Colors**: Green (Present), Amber (Late), Red (Absent)

## 🔧 Technologies Used

### Flutter App (Cross-Platform)
- **Framework**: Flutter 3.24+
- **Language**: Dart
- **State Management**: Provider
- **Backend**: Firebase
  - Firebase Authentication
  - Cloud Firestore
  - Firebase Messaging
- **QR Code**: qr_flutter, mobile_scanner
- **UI**: Material Design 3, Google Fonts
- **Local Storage**: SharedPreferences

### Android Native App (Legacy)
- **Language**: Kotlin
- **Architecture**: MVVM with Fragments
- **QR Code**: ZXing library
- **Background Tasks**: WorkManager

## 📋 Prerequisites

### For Flutter App (Recommended)

1. **Flutter SDK**
   - Version: 3.10.0 or later
   - Download: [Flutter Official Website](https://docs.flutter.dev/get-started/install)

2. **IDE**
   - VS Code with Flutter extension, OR
   - Android Studio with Flutter plugin

3. **Platform Tools**
   - **Android**: Android SDK 24+ (Android 7.0)
   - **iOS**: Xcode 14+ (macOS only)

### Firebase Setup

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Enable **Email/Password Authentication**
3. Enable **Cloud Firestore**
4. Download configuration files:
   - Android: `google-services.json` → `flutter_app/android/app/`
   - iOS: `GoogleService-Info.plist` → `flutter_app/ios/Runner/`

## 🚀 Installation

### Step 1: Clone the Repository

```bash
git clone https://github.com/badteplays/AttendanceSystem.git
cd AttendanceSystem
```

### Step 2: Flutter App Setup

```bash
cd flutter_app
flutter pub get
```

### Step 3: Configure Firebase

1. Copy your `google-services.json` to `flutter_app/android/app/`
2. Copy your `GoogleService-Info.plist` to `flutter_app/ios/Runner/`
3. Update `flutter_app/lib/firebase_options.dart` with your config

### Step 4: Run the App

```bash
# For Android
flutter run

# For iOS (macOS only)
flutter run -d ios

# Build release APK
flutter build apk --release

# Build iOS app
flutter build ios --release
```

## 📁 Project Structure

```
AttendanceSystem/
├── app/                          # Android Native App (Kotlin)
│   └── src/main/
│       ├── java/                 # Kotlin source files
│       └── res/                  # Android resources
│
├── flutter_app/                  # Flutter Cross-Platform App
│   ├── lib/
│   │   ├── core/
│   │   │   ├── models/          # Data models
│   │   │   ├── services/        # Firebase services
│   │   │   ├── theme/           # App theme & colors
│   │   │   └── widgets/         # Reusable widgets
│   │   └── features/
│   │       ├── auth/            # Login, Signup, Role Selection
│   │       ├── student/         # Student screens
│   │       ├── teacher/         # Teacher screens
│   │       └── qr/              # QR Scanner & Display
│   ├── android/                 # Android configuration
│   ├── ios/                     # iOS configuration
│   └── assets/                  # Images, icons
│
└── server/                      # Python backend (optional)
```

## 📊 Database Structure (Firestore)

```
├── users/
│   └── {userId}
│       ├── name: string
│       ├── email: string
│       ├── isTeacher: boolean
│       ├── isStudent: boolean
│       ├── section: string (students)
│       └── department: string (teachers)
│
├── schedules/
│   └── {scheduleId}
│       ├── subject: string
│       ├── section: string
│       ├── teacherId: string
│       ├── startTime: string
│       ├── endTime: string
│       ├── day: string
│       └── room: string
│
├── attendance/
│   └── {attendanceId}
│       ├── userId: string
│       ├── studentName: string
│       ├── sessionId: string
│       ├── teacherId: string
│       ├── scheduleId: string
│       ├── subject: string
│       ├── section: string
│       ├── timestamp: timestamp
│       └── status: string
│
├── attendance_sessions/
│   └── {sessionId}
│       ├── teacherId: string
│       ├── scheduleId: string
│       ├── createdAt: number
│       └── expiresAt: number
│
└── archived_attendance/
    └── {attendanceId}
        └── ... (same as attendance)
```

## 🔐 User Roles

### Student
- Mark attendance by scanning QR codes
- View personal schedule and attendance history
- Track monthly attendance statistics
- Receive class reminders

### Teacher
- Generate time-limited QR codes
- Monitor real-time attendance
- Manage class schedules
- Add attendance manually
- View analytics and reports

## 📋 Permissions Required

### Android
- **Camera**: QR code scanning
- **Notifications**: Class reminders
- **Vibration**: Haptic feedback

### iOS
- **Camera**: QR code scanning
- **Notifications**: Class reminders

## 🎯 App Icon

The app features a custom-designed icon with:
- Gradient background (Indigo → Violet → Pink)
- White checkmark circle (attendance confirmation)
- QR code pattern element
- Modern floating decorative elements

Generate icons using: `flutter_app/generate_icon.html`

## 🔗 Links

- **Repository**: [GitHub](https://github.com/badteplays/AttendanceSystem)
- **Flutter Docs**: [flutter.dev](https://flutter.dev)
- **Firebase Console**: [console.firebase.google.com](https://console.firebase.google.com)

---

**Version**: 2.0 (Flutter Cross-Platform)  
**Last Updated**: January 2026  
**Platforms**: Android, iOS  
**Min Android SDK**: 24 (Android 7.0)  
**Min iOS Version**: 12.0
