# Security Documentation - Attendance System

This document outlines the security measures implemented in the Attendance System app.

## 🔐 Authentication Security

### Firebase Authentication
- **Industry-standard**: Uses Firebase Authentication with email/password
- **Secure transmission**: All auth data transmitted over HTTPS/TLS
- **Token-based**: Firebase ID tokens with automatic refresh
- **Email verification**: Can be enabled for additional security

### Local Security
| Feature | Implementation |
|---------|---------------|
| Password Storage | BCrypt hashing with salt |
| Local Preferences | AES-256-GCM encrypted via EncryptedSharedPreferences |
| Session Management | 24-hour timeout with automatic expiry |
| Account Lockout | 5 failed attempts = 15 minute lockout |

## 🛡️ Data Security

### Firestore Security Rules
Our Firestore rules implement:
- **Role-based access control (RBAC)**: Teachers and students have different permissions
- **Owner validation**: Users can only access their own data
- **Data validation**: Required fields and format validation
- **Immutable records**: Attendance records cannot be modified after creation
- **Session validation**: QR scans validated against active sessions

### Key Security Rules:
```
// Students can only create attendance if:
// 1. Session exists and is active
// 2. Session is not expired
// 3. Required fields are present
// 4. Status is valid (Present/Late/Excused)
```

## 📱 QR Code Security

### Protection Measures
1. **Time-limited codes**: QR codes expire after configurable duration (default: 5 min)
2. **Session binding**: Each QR code is tied to a specific attendance session
3. **Teacher validation**: QR codes validated against session creator
4. **Duplicate prevention**: Server-side check prevents multiple scans
5. **Signature verification**: HMAC-SHA256 signature for integrity (optional)

### QR Code Data Structure
```json
{
  "teacherId": "firebase_uid",
  "sessionId": "unique_session_id",
  "scheduleId": "schedule_reference",
  "timestamp": 1702828800000,
  "expirationMinutes": 5
}
```

## ✅ Input Validation

### Validated Fields
| Field | Validation Rules |
|-------|-----------------|
| Email | RFC 5322 format, max 254 chars |
| Password | Min 6 chars, no whitespace-only |
| Name | Letters, spaces, hyphens only, max 100 chars |
| Section | Alphanumeric with hyphen, max 20 chars |
| Subject | Letters, numbers, spaces, punctuation, max 100 chars |

### Sanitization
All user inputs are sanitized to prevent:
- HTML/Script injection (`<`, `>`, `"`, `'`, `&`)
- Control character injection
- Excessive length attacks

## 🌐 Web App Security

### Implemented Measures
- **HTTPS only**: Camera access requires secure context
- **Firebase Auth**: Same authentication as Android app
- **Role restriction**: Only students can use web scanner
- **Input sanitization**: Client-side validation

### Recommendations for Production
1. Add CSP (Content Security Policy) headers
2. Enable Firebase App Check
3. Add domain restriction in Firebase Console
4. Use environment variables for Firebase config

## 🚨 Rate Limiting

### App-side Rate Limiting
- Max 30 requests per minute per operation type
- Automatic cooldown after limit reached
- Cleared after successful operations

### Firestore Rate Limiting
- Built-in Firebase quotas
- Custom rate limit rules (1 second between writes)

## 📋 Security Checklist

### Before Production Deployment

- [ ] Change `QR_SECRET_KEY` in SecurityUtils.kt
- [ ] Enable email verification in Firebase
- [ ] Add Firebase App Check
- [ ] Update Firestore rules to production mode
- [ ] Add domain restrictions in Firebase Console
- [ ] Enable audit logging
- [ ] Set up Firebase Security Alerts
- [ ] Review and restrict API key permissions

### Firebase Console Settings
1. **Authentication** → Settings → Authorized domains: Add only your domain
2. **Firestore** → Rules: Deploy the production rules
3. **App Check**: Enable for Android and Web
4. **API Keys**: Restrict to specific apps/domains

## 🔍 Security Monitoring

### Recommended Monitoring
- Firebase Authentication audit logs
- Firestore audit logs
- Failed login attempt alerts
- Unusual activity patterns

### Incident Response
1. Suspicious activity detected → Review Firebase audit logs
2. Unauthorized access → Revoke affected tokens
3. Data breach → Reset all user passwords, notify affected users

## 📞 Reporting Security Issues

If you discover a security vulnerability, please:
1. **Do NOT** create a public issue
2. Email the development team directly
3. Provide detailed reproduction steps
4. Allow reasonable time for fix before disclosure

---

**Last Updated**: December 2024
**Security Review**: Passed


