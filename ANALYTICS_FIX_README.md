# Analytics Permission Fix

## Problem
The teacher analytics was failing with a "permission denied" error because:
1. The query was trying to fetch attendance records without filtering by `teacherId` first
2. Firestore security rules require teachers to only read attendance where they are the owner
3. A composite index was needed for the query to work efficiently

## Solutions Applied

### 1. Fixed Query (TeacherAnalyticsFragment.kt)
- Added `.whereEqualTo("teacherId", currentUser.uid)` before timestamp filters
- This ensures the query respects Firestore security rules

### 2. Created Firestore Indexes (firestore.indexes.json)
- Defined composite indexes for the attendance collection
- Index on: `teacherId + timestamp` (for analytics queries)
- Additional indexes for other attendance queries used in the app

### 3. Added Fallback Logic
- If the main query fails (e.g., index not yet created), falls back to a simpler query
- Fallback fetches all attendance for the teacher without date filters
- Date filtering is done in-memory on the client side

## How to Deploy the Index

### Option 1: Using Firebase CLI (Recommended)
```bash
firebase deploy --only firestore:indexes
```

### Option 2: Manual Creation in Firebase Console
1. Open Firebase Console → Firestore Database → Indexes
2. Click "Create Index"
3. Collection: `attendance`
4. Add fields:
   - `teacherId` (Ascending)
   - `timestamp` (Ascending)
5. Click "Create"

**Note:** Index creation can take several minutes. The app will use the fallback query until the index is ready.

## Testing
1. Log in as a teacher
2. Navigate to Analytics tab
3. If you see "Setting up analytics..." message, wait a few minutes and try again
4. Once the index is created, analytics should load instantly

## What Analytics Shows
- **Total Classes**: Number of attendance records
- **Total Students**: Unique students who attended
- **Attendance Rate**: Percentage of PRESENT + LATE records
- **Punctuality Rate**: Percentage of PRESENT records only
- **Best Performing Class**: Class with highest attendance rate

## Filters
- **Period**: Last 7/30/90 days or All Time
- **Subject**: Filter by specific subject or all
- **Section**: Filter by specific section or all

