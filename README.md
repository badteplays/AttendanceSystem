# AttendanceSystem — QR Code Based Attendance Management

A modern **Android** attendance tracking application for educational institutions using QR code technology, with a web-based **Admin Dashboard** for account management.

## 📱 Overview

**AttendanceSystem** is a comprehensive attendance management solution that leverages QR code technology to enable quick and contactless attendance marking. The app provides separate interfaces for teachers and students, with real-time synchronization through Firebase. Accounts are managed by administrators through a dedicated web dashboard.

## ✨ Key Features

### 👨‍🎓 For Students

- **QR Code Scanner**
  - Fast and accurate QR code scanning for marking attendance
  - Real-time validation with duplicate prevention
  - Visual and haptic feedback on successful scan
  - Automatic timer display showing remaining time to scan

- **Dashboard**
  - Current class status display with animated statistics
  - Quick access to QR scanner
  - Monthly attendance breakdown (Present, Absent, Late)
  - Real-time attendance status updates

- **Class Schedule**
  - View complete weekly schedule
  - See enrolled classes by section
  - Color-coded active class indicators
  - Display class details (subject, room, time)

- **Profile**
  - View section and enrollment details
  - Manage notification preferences
  - Sign out with confirmation

### 👨‍🏫 For Teachers

- **QR Code Generation**
  - Generate unique QR codes per session
  - Configurable expiration (5–60 minutes)
  - Live countdown timer display
  - Regenerate codes instantly

- **Real-Time Attendance Dashboard**
  - Live attendance updates as students scan
  - See student names, sections, and scan times
  - Current class information display

- **Manual Attendance**
  - Manually mark students who forgot to scan
  - Search and select students by section

- **Schedule Management**
  - Create and manage class schedules
  - View schedules grouped by day
  - "TODAY" indicator badge

- **Analytics & Reporting**
  - Monthly attendance overview
  - Breakdown by status (Present, Late, Absent, Excused)
  - Animated percentage counters

### 🛡️ Admin Dashboard (Web)

- **Account Management** — Create, edit, and delete student/teacher accounts
- **Password Management** — Directly change any user's password
- **User Listing** — Search and filter all accounts by role
- **Deployed on Vercel** — Always online, no local setup needed

## 🎨 Design

- **Modern Dark Theme** — Deep blacks with layered surfaces
- **Gradient Accents** — Indigo → Violet color scheme
- **Material Design 3** — Clean, modern Android UI
- **Micro-animations** — Counters, transitions, and pulses

## 🔧 Tech Stack

### Android App
- **Language**: Kotlin
- **Architecture**: MVVM with Fragments
- **UI**: Material Design 3
- **QR Code**: ZXing library
- **Background Tasks**: WorkManager
- **Backend**: Firebase (Authentication, Cloud Firestore, Messaging)

### Admin Dashboard
- **Frontend**: HTML / CSS / JavaScript
- **Backend**: Vercel Serverless Functions (Node.js)
- **Auth Management**: Firebase Admin SDK

## 📋 Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17 or later
- Android SDK 24–35
- Firebase Project with Authentication and Firestore enabled

## 🚀 Setup

### Android App

1. Clone the repository
2. Open in Android Studio
3. Download `google-services.json` from [Firebase Console](https://console.firebase.google.com/) → Project Settings
4. Place it in `app/google-services.json`
5. Build and run

### Admin Dashboard

The admin dashboard is deployed on **Vercel** and accessible via URL — no local setup required.

To deploy your own:
1. Sign up at [vercel.com](https://vercel.com) with GitHub
2. Import this repo, set **Root Directory** to `web/admin`
3. Add environment variable `FIREBASE_SERVICE_ACCOUNT` with your Firebase service account JSON
4. Deploy — always online at your Vercel URL

## 📊 Database Structure

### Firestore Collections
| Collection | Description |
|---|---|
| `users` | User profiles (students and teachers) |
| `schedules` | Class schedules with time and section info |
| `attendance` | Active attendance records |
| `archived_attendance` | Historical attendance data |
| `attendance_sessions` | QR code session management |

## 🔐 User Roles

| Role | Capabilities |
|---|---|
| **Student** | Scan QR codes, view schedule, track attendance stats, receive reminders |
| **Teacher** | Generate QR codes, monitor live attendance, manage schedules, mark manually, view analytics |
| **Admin** | Create/edit/delete accounts, change passwords (via web dashboard) |

## 📱 App Structure

```
app/src/main/java/com/example/attendancesystem/
├── LoginActivity.kt              # Login screen (entry point)
├── RoleSelectionActivity.kt      # Student / Teacher selection
├── StudentMainActivity.kt        # Student container
├── TeacherMainActivity.kt        # Teacher container
├── StudentDashboardFragment.kt   # Student home
├── TeacherDashboardFragment.kt   # Teacher home
├── QRActivity.kt                 # QR generation (teacher)
├── QRScannerFragment.kt          # QR scanning (student)
├── TeacherSchedulesFragment.kt   # Schedule management
├── TeacherAnalyticsFragment.kt   # Analytics & reports
└── ...

web/admin/
├── index.html                    # Admin dashboard UI
├── admin.css                     # Styles
├── admin.js                      # Frontend logic
├── vercel.json                   # Vercel config
└── api/                          # Serverless API functions
    ├── lib/firebase.js           # Shared Firebase Admin init
    └── users/                    # User CRUD endpoints
```

## 🔗 Repository

[GitHub Repository](https://github.com/badteplays/AttendanceSystem)

---

**Version**: 1.0  
**Last Updated**: May 2026  
**Platform**: Android  
**Min Android SDK**: 24 (Android 7.0)
