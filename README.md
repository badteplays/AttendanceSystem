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

## 📋 Prerequisites

Before you begin, ensure you have the following requirements installed and configured:

### Required Software

1. **Android Studio**
   - **Version**: Android Studio Hedgehog (2023.1.1) or later
   - **Download**: [Android Studio Official Website](https://developer.android.com/studio)
   - **Why**: This is the official IDE for Android development and includes all necessary tools

2. **Java Development Kit (JDK)**
   - **Version**: JDK 17 or later
   - **Note**: Android Studio includes JDK, but you can also install separately
   - **Download**: [Oracle JDK](https://www.oracle.com/java/technologies/downloads/) or [OpenJDK](https://openjdk.org/)

3. **Android SDK**
   - **Minimum SDK**: 24 (Android 7.0 Nougat)
   - **Target SDK**: 35 (Android 15)
   - **Compile SDK**: 35
   - **Note**: SDK is automatically installed with Android Studio

4. **Git**
   - **Version**: 2.0 or later
   - **Download**: [Git Official Website](https://git-scm.com/downloads)
   - **Why**: Required to clone the repository

### Firebase Account & Setup

1. **Google Account**
   - A Google account is required to access Firebase Console
   - Sign up at [Firebase Console](https://console.firebase.google.com/)

2. **Firebase Project**
   - Create a new Firebase project (or use existing)
   - Enable the following services:
     - **Firebase Authentication** (Email/Password method)
     - **Cloud Firestore Database**
     - **Firebase Cloud Messaging** (optional, for push notifications)

### Testing Device/Emulator

1. **Physical Android Device** (Recommended)
   - Android 7.0 (API 24) or higher
   - USB debugging enabled
   - Camera support (for QR code features)

2. **Android Emulator** (Alternative)
   - Set up through Android Studio AVD Manager
   - Recommended: Android 10 (API 29) or higher
   - Camera support enabled in emulator settings

### System Requirements

**For Windows:**
- Windows 10 (64-bit) or later
- 8 GB RAM minimum (16 GB recommended)
- 8 GB available disk space (for Android Studio and SDK)
- 1280 x 800 minimum screen resolution

**For macOS:**
- macOS 10.14 (Mojave) or later
- 8 GB RAM minimum (16 GB recommended)
- 8 GB available disk space
- 1280 x 800 minimum screen resolution

**For Linux:**
- 64-bit distribution capable of running 32-bit applications
- GNU C Library (glibc) 2.19 or later
- 8 GB RAM minimum (16 GB recommended)
- 8 GB available disk space
- 1280 x 800 minimum screen resolution

### Additional Tools (Optional but Recommended)

- **Android Debug Bridge (ADB)**: Included with Android Studio
- **Gradle**: Included with Android Studio (version 8.0+)
- **Kotlin Plugin**: Included with Android Studio

## 🚀 Installation

### Step 1: Clone the Repository

Open your terminal/command prompt and run:
```bash
git clone https://github.com/badteplays/AttendanceSystem.git
cd AttendanceSystem
```

### Step 2: Open Project in Android Studio

1. Launch **Android Studio**
2. Click **File** → **Open** (or **Open an Existing Project**)
3. Navigate to the cloned `AttendanceSystem` folder
4. Click **OK** to open the project
5. Wait for Gradle sync to complete (this may take a few minutes on first open)

### Step 3: Configure Firebase

#### 3.1 Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **Add project** (or select existing project)
3. Enter project name: `AttendanceSystem` (or your preferred name)
4. Follow the setup wizard:
   - Enable/disable Google Analytics (optional)
   - Select or create Analytics account
5. Click **Create project**

#### 3.2 Add Android App to Firebase

1. In Firebase Console, click **Add app** → Select **Android** icon
2. Enter your app details:
   - **Android package name**: `com.example.attendancesystem`
   - **App nickname** (optional): AttendanceSystem
   - **Debug signing certificate SHA-1** (optional, for now)
3. Click **Register app**

#### 3.3 Download Configuration File

1. Download `google-services.json` file
2. In Android Studio, navigate to `app/` directory in Project view
3. Copy `google-services.json` into the `app/` directory (same level as `build.gradle`)
4. **Important**: Ensure the file is placed directly in `app/` folder, not in `app/src/`

#### 3.4 Enable Firebase Services

1. In Firebase Console, go to **Authentication**:
   - Click **Get started**
   - Enable **Email/Password** sign-in method
   - Click **Save**

2. In Firebase Console, go to **Firestore Database**:
   - Click **Create database**
   - Start in **test mode** (for development)
   - Select a location (choose closest to your region)
   - Click **Enable**

3. (Optional) In Firebase Console, go to **Cloud Messaging**:
   - This enables push notifications
   - No additional setup required for basic functionality

### Step 4: Sync and Build

1. In Android Studio, click **File** → **Sync Project with Gradle Files**
2. Wait for sync to complete (check bottom status bar)
3. If you see any errors, ensure:
   - `google-services.json` is in the correct location
   - Internet connection is active
   - All SDK components are installed

### Step 5: Run the App

#### Option A: Physical Device

1. Enable **Developer Options** on your Android device:
   - Go to **Settings** → **About phone**
   - Tap **Build number** 7 times
2. Enable **USB Debugging**:
   - Go to **Settings** → **Developer options**
   - Toggle **USB debugging** ON
3. Connect device via USB
4. In Android Studio, select your device from the device dropdown
5. Click **Run** button (green play icon) or press `Shift + F10`

#### Option B: Android Emulator

1. In Android Studio, click **Tools** → **Device Manager**
2. Click **Create Device**
3. Select a device (e.g., Pixel 5)
4. Select a system image (API 29 or higher recommended)
5. Click **Finish** to create the emulator
6. Start the emulator
7. In Android Studio, select the emulator from device dropdown
8. Click **Run** button or press `Shift + F10`

### Step 6: Verify Installation

After the app launches:
1. You should see the **Login** screen
2. Create a test account or sign in
3. Verify that Firebase connection is working (no connection errors)

### Troubleshooting

**Gradle Sync Failed:**
- Check internet connection
- Verify `google-services.json` is in `app/` directory
- Try **File** → **Invalidate Caches** → **Invalidate and Restart**

**Build Errors:**
- Ensure Android SDK is properly installed
- Check that all required SDK components are downloaded
- Verify Kotlin plugin is enabled

**Firebase Connection Issues:**
- Verify `google-services.json` package name matches `build.gradle`
- Check Firebase project settings
- Ensure Authentication and Firestore are enabled

**App Crashes on Launch:**
- Check Logcat for error messages
- Verify all permissions are properly declared in `AndroidManifest.xml`
- Ensure minimum SDK version matches device/emulator

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
- **Material Design**: Modern and intuitive user interface
- **Dark Mode**: System-wide theme support
- **Security**: Session-based QR codes with expiration and duplicate prevention
- **Scalability**: Designed to handle multiple classes and sections simultaneously
- **Performance**: Optimized queries and efficient data handling
- **Cross-Platform**: Web PWA available for iOS users to scan QR codes

## 🐛 Bug Fixes & Improvements

Recent updates include:
- Fixed live attendance updates for teachers
- Improved QR scanner state management
- Added backward compatibility for section data
- Enhanced notification system for Android 13+
- Fixed race conditions in login flow
- Improved permission request handling

## 🔗 Repository

[GitHub Repository](https://github.com/badteplays/AttendanceSystem)

---

**Version**: 1.0  
**Last Updated**: November 2025
**Min SDK**: 24 (Android 7.0)  
**Target SDK**: 35 (Android 15)


** As of right now the Teacher recent attendance recyler is having problemssss**
** Same wit the Student Status not changing after class ends**
