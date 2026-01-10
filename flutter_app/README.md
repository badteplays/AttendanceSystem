# AttendanceSystem Flutter App

Cross-platform attendance tracking app for Android and iOS.

## Quick Start

### 1. Install Flutter
Download from: https://docs.flutter.dev/get-started/install

### 2. Configure Firebase
Copy your Firebase config files:
- `google-services.json` → `android/app/`
- `GoogleService-Info.plist` → `ios/Runner/`

Update `lib/firebase_options.dart` with your Firebase project settings.

### 3. Install Dependencies
```bash
flutter pub get
```

### 4. Run the App
```bash
flutter run
```

## Build Release

### Android APK
```bash
flutter build apk --release
```
Output: `build/app/outputs/flutter-apk/app-release.apk`

### iOS (macOS only)
```bash
flutter build ios --release
```

## Generate App Icon

1. Open `generate_icon.html` in a browser
2. Download the 1024x1024 icon as `app_icon.png`
3. Place in `assets/icon/`
4. Run:
```bash
dart run flutter_launcher_icons
```

## Project Structure

```
lib/
├── main.dart                    # App entry point
├── firebase_options.dart        # Firebase config
├── core/
│   ├── models/                  # Data models
│   ├── services/                # Auth & Firestore
│   ├── theme/                   # Colors & theme
│   └── widgets/                 # Reusable widgets
└── features/
    ├── auth/                    # Login, Signup
    ├── student/                 # Student screens
    ├── teacher/                 # Teacher screens
    └── qr/                      # QR Scanner & Display
```

## Dependencies

- firebase_core, firebase_auth, cloud_firestore
- provider (state management)
- qr_flutter (QR generation)
- mobile_scanner (QR scanning)
- google_fonts (typography)
- intl (date formatting)

## Requirements

- Flutter 3.10+
- Dart 3.0+
- Android SDK 24+ (Android 7.0)
- iOS 12.0+
