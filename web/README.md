# Attendance Scanner - Web PWA

A Progressive Web App for iOS users to scan QR codes and mark attendance. This web app integrates with the same Firebase backend as the Android app.

## 🚀 Quick Start

### 1. Add Web App to Firebase

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: `qrsystemattendance`
3. Click the gear icon → **Project settings**
4. Scroll to **Your apps** section
5. Click **Add app** → Select **Web** (</> icon)
6. Register your app with a nickname (e.g., "Attendance Web")
7. Copy the `appId` from the config and update it in `app.js`:

```javascript
const firebaseConfig = {
    // ... other config
    appId: "1:26091166021:web:YOUR_WEB_APP_ID_HERE"
};
```

### 2. Create PWA Icons

Create icon images in the `icons/` folder. See `icons/placeholder.txt` for required sizes.

### 3. Deploy to Firebase Hosting

```bash
# Install Firebase CLI if not already installed
npm install -g firebase-tools

# Login to Firebase
firebase login

# Initialize hosting (run from web folder)
cd web
firebase init hosting

# When prompted:
# - Select your project: qrsystemattendance
# - Public directory: . (current directory)
# - Single-page app: Yes
# - Don't overwrite index.html

# Deploy
firebase deploy --only hosting
```

Your web app will be available at:
- `https://qrsystemattendance.web.app`
- `https://qrsystemattendance.firebaseapp.com`

## 📱 Features

- **Student Registration** - iOS students can create accounts directly
- **Student Login** - Uses same Firebase Auth as Android app
- **QR Scanner** - Camera-based QR code scanning
- **Real-time Attendance** - Records attendance to Firestore
- **PWA Support** - Can be installed to iOS home screen
- **Responsive Design** - Works on all screen sizes

## 🔧 Local Development

To test locally, you need to serve the files over HTTPS (required for camera access):

### Option 1: Firebase Emulator
```bash
firebase emulators:start --only hosting
```

### Option 2: Live Server with HTTPS
```bash
# Using Python
python -m http.server 8000

# Then access via localhost (camera works on localhost)
```

### Option 3: Use a tool like ngrok
```bash
ngrok http 8000
```

## 📋 How It Works

1. **New students can sign up** directly on the web app (or use existing Android app account)
2. **Student logs in** with their email/password
3. **Camera activates** and shows the QR scanner
4. **Student scans** the QR code displayed by teacher
5. **Attendance is recorded** to Firestore with:
   - Student info (ID, name, section)
   - Session info (subject, teacher, time)
   - Marked as "Present" via "web_scanner"
5. **Success confirmation** is shown with class details

## 🔐 Security

- Only students can use this web app (teachers are blocked)
- QR codes are validated against active sessions
- Duplicate scans are prevented
- Expired QR codes are rejected

## 🎨 Customization

### Colors
Edit CSS variables in `index.html`:
```css
:root {
    --accent-primary: #22d3ee;
    --bg-primary: #0f172a;
    /* ... */
}
```

### Logo
Replace the emoji in `.logo-icon` with an image or SVG.

## ⚠️ Important Notes

1. **Camera Access**: Requires HTTPS in production (localhost works for development)
2. **iOS Safari**: Camera works but may require user permission each time
3. **Firebase Config**: Update the `appId` in `app.js` after adding web app to Firebase
4. **Icons**: Create proper PWA icons for home screen installation

## 🐛 Troubleshooting

**Camera not working:**
- Ensure HTTPS is enabled
- Check browser permissions
- Try refreshing the page

**Login fails:**
- Verify Firebase Auth is enabled
- Check if user exists in Firestore
- Ensure user role is "student"

**QR scan fails:**
- Check if attendance session is active
- Verify QR code hasn't expired
- Ensure student hasn't already scanned

