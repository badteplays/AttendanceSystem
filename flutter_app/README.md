# AttendanceSystem - Flutter App

Cross-platform attendance tracking app built with Flutter for Android and iOS.

## 🚀 Quick Start

### Prerequisites
- Flutter SDK 3.10.0+
- Firebase project with Auth & Firestore enabled

### Setup

1. **Install dependencies:**
   ```bash
   flutter pub get
   ```

2. **Configure Firebase:**
   
   **Android:**
   - Copy `google-services.json` to `android/app/`
   
   **iOS:**
   - Copy `GoogleService-Info.plist` to `ios/Runner/`

3. **Update Firebase Options:**
   - Run `flutterfire configure` OR
   - Manually update `lib/firebase_options.dart` with your config

4. **Run the app:**
   ```bash
   flutter run
   ```

## 📱 Features

### Student
- Scan QR codes to mark attendance
- View schedule and attendance history
- Monthly statistics dashboard
- Beautiful animated UI

### Teacher
- Generate time-limited QR codes
- Live attendance monitoring
- Manual student addition
- Analytics dashboard

## 🎨 Design

- Dark theme with gradient accents
- Space Grotesk typography
- Animated counters and transitions
- Glass morphism cards

## 📁 Structure

```
lib/
├── core/
│   ├── models/        # Attendance, Schedule, QRCodeData, User
│   ├── services/      # AuthService, FirestoreService
│   ├── theme/         # AppColors, AppTheme
│   └── widgets/       # GradientCard, AnimatedCounter
├── features/
│   ├── auth/          # Login, Signup, RoleSelection
│   ├── student/       # Dashboard, Schedule, History, Options
│   ├── teacher/       # Dashboard, Schedules, Analytics, Options
│   └── qr/            # Scanner, Display
└── main.dart
```

## 🔧 Generate App Icons

```bash
dart run flutter_launcher_icons
```

## 📋 Firestore Collections

- `users` - User profiles
- `schedules` - Class schedules
- `attendance` - Attendance records
- `attendance_sessions` - QR sessions
- `archived_attendance` - Historical data

## 🔑 Environment

Update `lib/firebase_options.dart` with your Firebase config before running.
