// Firebase Configuration
const firebaseConfig = {
    apiKey: "AIzaSyDmyE0eWS7FEzyiZsLKwbzgtzWsOws5lMg",
    authDomain: "qrsystemattendance.firebaseapp.com",
    projectId: "qrsystemattendance",
    storageBucket: "qrsystemattendance.firebasestorage.app",
    messagingSenderId: "26091166021",
    appId: "1:26091166021:web:your_web_app_id" // You'll need to add web app in Firebase console
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const db = firebase.firestore();

// App State
let currentUser = null;
let userData = null;
let html5QrCode = null;
let currentCameraId = null;
let cameras = [];

// DOM Elements
const screens = {
    login: document.getElementById('loginScreen'),
    signup: document.getElementById('signupScreen'),
    scanner: document.getElementById('scannerScreen'),
    success: document.getElementById('successScreen'),
    error: document.getElementById('errorScreen'),
    loading: document.getElementById('loadingScreen')
};

// Show specific screen
function showScreen(screenName) {
    Object.values(screens).forEach(screen => screen.classList.remove('active'));
    screens[screenName].classList.add('active');
}

// Toast notification
function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    const toastIcon = document.getElementById('toastIcon');
    const toastMessage = document.getElementById('toastMessage');
    
    toastIcon.textContent = type === 'success' ? '✓' : '✕';
    toastMessage.textContent = message;
    toast.className = `toast ${type}`;
    toast.classList.add('show');
    
    setTimeout(() => {
        toast.classList.remove('show');
    }, 3000);
}

// Handle Login
async function handleLogin(event) {
    event.preventDefault();
    
    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;
    const loginBtn = document.getElementById('loginBtn');
    
    loginBtn.disabled = true;
    loginBtn.textContent = 'Signing in...';
    
    try {
        const userCredential = await auth.signInWithEmailAndPassword(email, password);
        currentUser = userCredential.user;
        
        // Get user data from Firestore
        const userDoc = await db.collection('users').doc(currentUser.uid).get();
        
        if (!userDoc.exists) {
            throw new Error('User profile not found');
        }
        
        userData = userDoc.data();
        
        // Check if user is a student
        if (userData.role !== 'student') {
            throw new Error('This web app is only for students. Teachers should use the Android app.');
        }
        
        showToast('Welcome back!');
        initializeScannerScreen();
        
    } catch (error) {
        console.error('Login error:', error);
        showToast(getErrorMessage(error), 'error');
    } finally {
        loginBtn.disabled = false;
        loginBtn.textContent = 'Sign In';
    }
}

// Get user-friendly error messages
function getErrorMessage(error) {
    const errorMessages = {
        'auth/user-not-found': 'No account found with this email',
        'auth/wrong-password': 'Incorrect password',
        'auth/invalid-email': 'Invalid email address',
        'auth/too-many-requests': 'Too many attempts. Please try again later',
        'auth/network-request-failed': 'Network error. Check your connection',
        'auth/email-already-in-use': 'This email is already registered. Please sign in.',
        'auth/weak-password': 'Password is too weak. Use at least 6 characters.'
    };
    
    return errorMessages[error.code] || error.message || 'An error occurred';
}

// Security: Input validation functions
function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return email && emailRegex.test(email) && email.length <= 254;
}

function isValidName(name) {
    const nameRegex = /^[\p{L}\s.'-]+$/u;
    return name && nameRegex.test(name) && name.length <= 100;
}

function isValidSection(section) {
    const sectionRegex = /^[A-Z0-9-]+$/;
    return section && sectionRegex.test(section) && section.length <= 20;
}

function sanitizeString(input) {
    if (!input) return '';
    return input.trim()
        .replace(/[<>"'&]/g, '')
        .substring(0, 500);
}

// Handle Signup
async function handleSignup(event) {
    event.preventDefault();
    
    const name = sanitizeString(document.getElementById('signupName').value);
    const email = document.getElementById('signupEmail').value.trim().toLowerCase();
    const section = document.getElementById('signupSection').value.trim().toUpperCase().replace(/[^A-Z0-9-]/g, '');
    const password = document.getElementById('signupPassword').value;
    const confirmPassword = document.getElementById('signupConfirmPassword').value;
    const signupBtn = document.getElementById('signupBtn');
    
    // Security: Enhanced validation
    if (!isValidEmail(email)) {
        showToast('Please enter a valid email address', 'error');
        return;
    }
    
    if (!isValidName(name)) {
        showToast('Please enter a valid name (letters only)', 'error');
        return;
    }
    
    if (!isValidSection(section)) {
        showToast('Please enter a valid section (e.g., BSIT-3A)', 'error');
        return;
    }
    
    if (password !== confirmPassword) {
        showToast('Passwords do not match', 'error');
        return;
    }
    
    if (password.length < 6 || password.length > 128) {
        showToast('Password must be 6-128 characters', 'error');
        return;
    }
    
    signupBtn.disabled = true;
    signupBtn.textContent = 'Creating Account...';
    
    try {
        // Create user in Firebase Auth
        const userCredential = await auth.createUserWithEmailAndPassword(email, password);
        const user = userCredential.user;
        
        // Create user document in Firestore
        const newUserData = {
            email: email,
            name: name,
            section: section,
            role: 'student',
            isStudent: true,
            isTeacher: false,
            createdAt: Date.now(),
            createdVia: 'web_app'
        };
        
        await db.collection('users').doc(user.uid).set(newUserData);
        
        // Set local state
        currentUser = user;
        userData = newUserData; // Set the global userData variable
        
        showToast('Account created successfully!');
        initializeScannerScreen();
        
    } catch (error) {
        console.error('Signup error:', error);
        showToast(getErrorMessage(error), 'error');
    } finally {
        signupBtn.disabled = false;
        signupBtn.textContent = 'Create Account';
    }
}

// Initialize Scanner Screen
function initializeScannerScreen() {
    // Update UI
    document.getElementById('userInfo').classList.remove('hidden');
    document.getElementById('userName').textContent = userData.name || userData.email;
    document.getElementById('welcomeText').textContent = `Welcome, ${userData.name || 'Student'}!`;
    
    showScreen('scanner');
    initializeQRScanner();
}

// Initialize QR Scanner
async function initializeQRScanner() {
    try {
        html5QrCode = new Html5Qrcode("qr-reader");
        
        // Get available cameras
        cameras = await Html5Qrcode.getCameras();
        
        if (cameras && cameras.length > 0) {
            // Prefer back camera
            const backCamera = cameras.find(camera => 
                camera.label.toLowerCase().includes('back') || 
                camera.label.toLowerCase().includes('rear') ||
                camera.label.toLowerCase().includes('environment')
            );
            
            currentCameraId = backCamera ? backCamera.id : cameras[0].id;
            startScanner();
        } else {
            showToast('No camera found', 'error');
        }
    } catch (error) {
        console.error('Camera initialization error:', error);
        showToast('Camera access denied', 'error');
    }
}

// Start Scanner
async function startScanner() {
    try {
        await html5QrCode.start(
            currentCameraId,
            {
                fps: 10,
                qrbox: { width: 250, height: 250 },
                aspectRatio: 1.0
            },
            onScanSuccess,
            onScanFailure
        );
    } catch (error) {
        console.error('Scanner start error:', error);
        
        // Try with facingMode if specific camera fails
        try {
            await html5QrCode.start(
                { facingMode: "environment" },
                {
                    fps: 10,
                    qrbox: { width: 250, height: 250 },
                    aspectRatio: 1.0
                },
                onScanSuccess,
                onScanFailure
            );
        } catch (fallbackError) {
            console.error('Fallback scanner error:', fallbackError);
            showToast('Could not start camera', 'error');
        }
    }
}

// Toggle Camera
async function toggleCamera() {
    if (cameras.length < 2) {
        showToast('Only one camera available', 'error');
        return;
    }
    
    try {
        await html5QrCode.stop();
        
        // Switch to next camera
        const currentIndex = cameras.findIndex(c => c.id === currentCameraId);
        const nextIndex = (currentIndex + 1) % cameras.length;
        currentCameraId = cameras[nextIndex].id;
        
        await startScanner();
        showToast('Camera switched');
    } catch (error) {
        console.error('Camera toggle error:', error);
        showToast('Could not switch camera', 'error');
    }
}

// QR Code Scan Success
async function onScanSuccess(decodedText, decodedResult) {
    // Stop scanning immediately to prevent multiple scans
    try {
        await html5QrCode.stop();
    } catch (e) {
        // Ignore stop errors
    }
    
    showScreen('loading');
    
    try {
        // Parse QR code data
        let qrData;
        try {
            qrData = JSON.parse(decodedText);
        } catch {
            // If not JSON, treat as simple session ID
            qrData = { sessionId: decodedText };
        }
        
        // Validate and record attendance
        await recordAttendance(qrData);
        
    } catch (error) {
        console.error('Scan processing error:', error);
        document.getElementById('errorMessage').textContent = error.message || 'Failed to record attendance';
        showScreen('error');
    }
}

// QR Code Scan Failure (called frequently when no QR detected - ignore)
function onScanFailure(error) {
    // Silently ignore - this is called frequently when no QR is in view
}

// Record Attendance
async function recordAttendance(qrData) {
    const sessionId = qrData.sessionId || qrData.data || qrData;
    
    // Find the attendance session
    const sessionQuery = await db.collection('attendance_sessions')
        .where('qrData', '==', sessionId)
        .where('active', '==', true)
        .limit(1)
        .get();
    
    if (sessionQuery.empty) {
        throw new Error('Invalid or expired QR code. Please ask your teacher to generate a new one.');
    }
    
    const sessionDoc = sessionQuery.docs[0];
    const sessionData = sessionDoc.data();
    
    // Check if session has expired
    if (sessionData.expiresAt && sessionData.expiresAt.toDate() < new Date()) {
        throw new Error('This QR code has expired. Please ask your teacher to renew it.');
    }
    
    // Check if student has already scanned
    const existingAttendance = await db.collection('attendance')
        .where('sessionId', '==', sessionDoc.id)
        .where('studentId', '==', currentUser.uid)
        .limit(1)
        .get();
    
    if (!existingAttendance.empty) {
        throw new Error('You have already marked your attendance for this class.');
    }
    
    // Get schedule info for display
    let scheduleData = null;
    if (sessionData.scheduleId) {
        const scheduleDoc = await db.collection('schedules').doc(sessionData.scheduleId).get();
        if (scheduleDoc.exists) {
            scheduleData = scheduleDoc.data();
        }
    }
    
    // Record the attendance
    const attendanceRecord = {
        studentId: currentUser.uid,
        studentName: userData.name || '',
        studentEmail: userData.email || currentUser.email,
        section: userData.section || '',
        sessionId: sessionDoc.id,
        scheduleId: sessionData.scheduleId || '',
        teacherId: sessionData.teacherId || '',
        subject: sessionData.subject || scheduleData?.subject || 'Unknown',
        timestamp: firebase.firestore.FieldValue.serverTimestamp(),
        status: 'Present',
        markedVia: 'web_scanner'
    };
    
    await db.collection('attendance').add(attendanceRecord);
    
    // Show success screen
    showSuccessScreen({
        subject: attendanceRecord.subject,
        section: userData.section,
        time: new Date().toLocaleTimeString('en-US', { 
            hour: '2-digit', 
            minute: '2-digit',
            hour12: true 
        }),
        date: new Date().toLocaleDateString('en-US', {
            weekday: 'long',
            month: 'short',
            day: 'numeric'
        })
    });
}

// Show Success Screen
function showSuccessScreen(details) {
    const classDetails = document.getElementById('classDetails');
    classDetails.innerHTML = `
        <div class="class-info-item">
            <span class="class-info-label">Subject</span>
            <span class="class-info-value">${details.subject}</span>
        </div>
        <div class="class-info-item">
            <span class="class-info-label">Section</span>
            <span class="class-info-value">${details.section || 'N/A'}</span>
        </div>
        <div class="class-info-item">
            <span class="class-info-label">Time</span>
            <span class="class-info-value">${details.time}</span>
        </div>
        <div class="class-info-item">
            <span class="class-info-label">Date</span>
            <span class="class-info-value">${details.date}</span>
        </div>
    `;
    
    showScreen('success');
}

// Reset Scanner
async function resetScanner() {
    showScreen('scanner');
    await startScanner();
}

// Logout
async function logout() {
    try {
        if (html5QrCode) {
            try {
                await html5QrCode.stop();
            } catch (e) {
                // Ignore stop errors
            }
        }
        
        await auth.signOut();
        currentUser = null;
        userData = null;
        
        document.getElementById('userInfo').classList.add('hidden');
        
        // Clear all form fields
        document.getElementById('email').value = '';
        document.getElementById('password').value = '';
        document.getElementById('signupName').value = '';
        document.getElementById('signupEmail').value = '';
        document.getElementById('signupSection').value = '';
        document.getElementById('signupPassword').value = '';
        document.getElementById('signupConfirmPassword').value = '';
        
        showScreen('login');
        showToast('Logged out successfully');
        
    } catch (error) {
        console.error('Logout error:', error);
        showToast('Error logging out', 'error');
    }
}

// Check auth state on load
auth.onAuthStateChanged(async (user) => {
    if (user) {
        currentUser = user;
        
        try {
            const userDoc = await db.collection('users').doc(user.uid).get();
            
            if (userDoc.exists) {
                userData = userDoc.data();
                
                if (userData.role === 'student') {
                    initializeScannerScreen();
                    return;
                }
            }
        } catch (error) {
            console.error('Auth state check error:', error);
        }
        
        // If we get here, sign out
        await auth.signOut();
    }
    
    showScreen('login');
});

// Service Worker Registration (for PWA)
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('sw.js')
            .then(registration => {
                console.log('ServiceWorker registered:', registration.scope);
            })
            .catch(error => {
                console.log('ServiceWorker registration failed:', error);
            });
    });
}

