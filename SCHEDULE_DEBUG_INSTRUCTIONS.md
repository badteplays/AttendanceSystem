# Schedule Debug Instructions

## ✅ Enhanced Logging Added

I've added detailed logging to track if schedules are being saved and loaded. Here's how to check:

---

## 📝 How to Test Schedule Saving

### Step 1: Create a Schedule (Teacher Side)

1. **Login as Teacher**
2. **Go to Schedules Tab**
3. **Create a new schedule** with:
   - Subject: Test Subject
   - Section: MAWD402
   - Day: Monday
   - Start Time: 10:00 AM
   - End Time: 11:00 AM
4. **Click Save**

### Step 2: Check Logcat Logs (TEACHER)

Open **Android Studio → Logcat** and search for: `TeacherSchedules`

You should see:
```
=== SAVING SCHEDULE TO FIRESTORE ===
teacherId: abc123...
subject: Test Subject
section: MAWD402
day: Monday
startTime: 10:00
endTime: 11:00
✓✓✓ Schedule SAVED successfully! Document ID: xyz789... ✓✓✓

=== LOADING SCHEDULES FOR TEACHER: abc123... ===
Found 4 schedules in Firestore
  → Schedule xyz789...: Test Subject | MAWD402 | Monday | 10:00-11:00
  → Schedule abc456...: sisibnaan | MAWD402 | Monday | 12:52-17:52
  → Schedule def789...: PROGPROG | MAWD402 | Monday | 03:00-05:51
  → Schedule ghi012...: EAPP | MAWD402 | Monday | 12:51-14:51
```

**If you DON'T see "✓✓✓ Schedule SAVED successfully"**, then schedules are NOT being saved!

---

### Step 3: Check Logcat Logs (STUDENT)

**Login as Student** and go to **Schedule Tab**

Search for: `StudentScheduleFragment`

You should see:
```
Student section from database: 'mawd402'
Loading schedules for section: 'MAWD402' (original: 'mawd402')
Found 4 schedules
Found schedule: subject=Test Subject, section=MAWD402, day=Monday, time=10:00-11:00
Found schedule: subject=sisibnaan, section=MAWD402, day=Monday, time=12:52-17:52
Found schedule: subject=PROGPROG, section=MAWD402, day=Monday, time=03:00-05:51
Found schedule: subject=EAPP, section=MAWD402, day=Monday, time=12:51-14:51
Loaded 4 attendance records
```

---

## 🔍 What to Look For

### ✅ **If Schedules ARE Being Saved:**
- You'll see `✓✓✓ Schedule SAVED successfully! Document ID: ...`
- The Document ID will be shown in the Toast message
- Teacher schedule list will refresh and show the new schedule

### ❌ **If Schedules Are NOT Being Saved:**
- You'll see `✗✗✗ FAILED to save schedule: [error message]`
- Toast will show the error message
- Check Firebase Console → Firestore → `schedules` collection to verify

---

## 🐛 Common Issues

### Issue 1: Only 1 Schedule Shows on Student Side

**Possible Causes:**
1. **Section Mismatch**
   - Student section: `mawd402` (lowercase)
   - Schedule section: `MAWD 402` (with space)
   - **Solution:** Check logs for exact section values

2. **Only 1 Schedule Was Created**
   - Check Firebase Console → `schedules` collection
   - Count how many documents have `section: "MAWD402"`

3. **Student Assigned to Different Section**
   - Check Firebase Console → `users` collection → student document
   - Verify the `section` field matches schedule sections

---

## 📊 Firebase Console Check

1. Open **Firebase Console** (https://console.firebase.google.com)
2. Select your project
3. Go to **Firestore Database**
4. Check `schedules` collection:
   - Count documents
   - Check `section` field values (MAWD402, mawd402, MAWD 402, etc.)
   - Note the teacherId values
5. Check `users` collection:
   - Find the student document
   - Check their `section` field value

---

## 📤 Share These Logs

If schedules still aren't showing correctly, share these logs with me:

1. **Teacher logs** when creating a schedule (search: `TeacherSchedules`)
2. **Teacher logs** when loading schedules (search: `LOADING SCHEDULES`)
3. **Student logs** when viewing schedule (search: `StudentScheduleFragment`)
4. **Screenshot of Firebase Console** showing:
   - `schedules` collection (all documents)
   - `users` collection (student document with section field)

---

## 🎯 Expected Behavior

**Teacher Side:**
- Create schedule → Save → Toast shows "Schedule created (ID: xyz123)"
- Schedule appears in teacher's schedule list immediately

**Student Side:**
- Open schedule tab → See all classes for their section
- Each class shows: **Day Time** (e.g., "Monday 10:00 AM - 11:00 AM")
- Header shows total count (e.g., "4 classes")

---

## ✨ Changes Made

1. ✅ Added detailed save logging (shows all fields being saved)
2. ✅ Added document ID in success message
3. ✅ Added load logging (shows all schedules retrieved)
4. ✅ Fixed section normalization on edit
5. ✅ Added day of week to student schedule display
6. ✅ Added trimming to remove whitespace issues


