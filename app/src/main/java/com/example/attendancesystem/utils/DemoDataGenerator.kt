package com.example.attendancesystem.utils

import com.example.attendancesystem.models.AttendanceStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import kotlin.random.Random
import kotlinx.coroutines.tasks.await

class DemoDataGenerator {
    
    private val db = FirebaseFirestore.getInstance()
    
    companion object {
        private val DEMO_SUBJECTS = listOf(
            "Mathematics", "Physics", "Chemistry", "Biology", "English", 
            "History", "Geography", "Computer Science", "Art", "Music"
        )
        
        private val DEMO_SECTIONS = listOf("A", "B", "C")
        
        private val DEMO_STUDENT_NAMES = listOf(
            "Alice Johnson", "Bob Smith", "Charlie Brown", "Diana Prince", "Edward Norton",
            "Fiona Apple", "George Washington", "Hannah Montana", "Ivan Drago", "Jessica Jones",
            "Kevin Hart", "Luna Lovegood", "Michael Jordan", "Nancy Drew", "Oliver Queen",
            "Pam Beesly", "Quincy Jones", "Rachel Green", "Steve Rogers", "Tina Turner",
            "Uma Thurman", "Victor Hugo", "Wendy Darling", "Xavier Charles", "Yoda Master",
            "Zoe Saldana", "Amy Adams", "Brian Cox", "Catherine Zeta", "Daniel Craig"
        )
        
        private val DEMO_TEACHER_NAMES = listOf(
            "Prof. Anderson", "Dr. Williams", "Ms. Martinez", "Mr. Thompson", "Dr. Garcia",
            "Prof. Rodriguez", "Ms. Davis", "Mr. Wilson", "Dr. Taylor", "Prof. Brown"
        )
        
        private val DEMO_DEPARTMENTS = listOf(
            "Mathematics Department", "Science Department", "Language Arts", 
            "Social Studies", "Computer Science", "Arts Department"
        )
    }
    
    data class DemoStudent(
        val id: String,
        val name: String,
        val email: String,
        val section: String
    )
    
    data class DemoTeacher(
        val id: String,
        val name: String,
        val email: String,
        val department: String
    )
    
    data class DemoSchedule(
        val id: String,
        val subject: String,
        val section: String,
        val teacherId: String,
        val teacherName: String,
        val startTime: String,
        val endTime: String,
        val day: String,
        val room: String
    )
    
    suspend fun generateDemoData(): Result<String> {
        return try {
            // Generate demo students
            val students = generateDemoStudents()
            val teachers = generateDemoTeachers()
            val schedules = generateDemoSchedules(teachers)
            
            // Save to Firestore
            saveDemoUsers(students, teachers)
            saveDemoSchedules(schedules)
            generateDemoAttendanceData(students, schedules)
            
            Result.success("Demo data generated successfully!\n" +
                    "Generated:\n" +
                    "- ${students.size} students\n" +
                    "- ${teachers.size} teachers\n" +
                    "- ${schedules.size} schedules\n" +
                    "- Attendance data for the past 30 days")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun generateDemoStudents(): List<DemoStudent> {
        return DEMO_STUDENT_NAMES.mapIndexed { index, name ->
            DemoStudent(
                id = "student_$index",
                name = name,
                email = "${name.replace(" ", ".").lowercase()}@student.edu",
                section = DEMO_SECTIONS[index % DEMO_SECTIONS.size]
            )
        }
    }
    
    private fun generateDemoTeachers(): List<DemoTeacher> {
        return DEMO_TEACHER_NAMES.mapIndexed { index, name ->
            DemoTeacher(
                id = "teacher_$index",
                name = name,
                email = "${name.replace(" ", ".").replace(".", "").lowercase()}@school.edu",
                department = DEMO_DEPARTMENTS[index % DEMO_DEPARTMENTS.size]
            )
        }
    }
    
    private fun generateDemoSchedules(teachers: List<DemoTeacher>): List<DemoSchedule> {
        val schedules = mutableListOf<DemoSchedule>()
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
        val timeSlots = listOf(
            "08:00" to "09:30",
            "09:45" to "11:15",
            "11:30" to "13:00",
            "14:00" to "15:30",
            "15:45" to "17:15"
        )
        
        teachers.forEachIndexed { teacherIndex, teacher ->
            val subjectsForTeacher = DEMO_SUBJECTS.shuffled().take(Random.nextInt(2, 4))
            
            subjectsForTeacher.forEach { subject ->
                val sectionsForSubject = DEMO_SECTIONS.shuffled().take(Random.nextInt(1, 3))
                
                sectionsForSubject.forEach { section ->
                    val randomDay = days.random()
                    val randomTimeSlot = timeSlots.random()
                    
                    schedules.add(
                        DemoSchedule(
                            id = "schedule_${teacherIndex}_${subject}_${section}",
                            subject = subject,
                            section = section,
                            teacherId = teacher.id,
                            teacherName = teacher.name,
                            startTime = randomTimeSlot.first,
                            endTime = randomTimeSlot.second,
                            day = randomDay,
                            room = "Room ${Random.nextInt(101, 350)}"
                        )
                    )
                }
            }
        }
        
        return schedules
    }
    
    private suspend fun saveDemoUsers(students: List<DemoStudent>, teachers: List<DemoTeacher>) {
        val batch = db.batch()
        
        // Save students
        students.forEach { student ->
            val userDoc = db.collection("users").document(student.id)
            val userData = mapOf(
                "email" to student.email,
                "name" to student.name,
                "role" to "student",
                "isTeacher" to false,
                "isStudent" to true,
                "section" to student.section,
                "createdAt" to System.currentTimeMillis(),
                "isDemo" to true
            )
            batch.set(userDoc, userData)
        }
        
        // Save teachers
        teachers.forEach { teacher ->
            val userDoc = db.collection("users").document(teacher.id)
            val userData = mapOf(
                "email" to teacher.email,
                "name" to teacher.name,
                "role" to "teacher",
                "isTeacher" to true,
                "isStudent" to false,
                "department" to teacher.department,
                "createdAt" to System.currentTimeMillis(),
                "isDemo" to true
            )
            batch.set(userDoc, userData)
        }
        
        batch.commit()
    }
    
    private suspend fun saveDemoSchedules(schedules: List<DemoSchedule>) {
        val batch = db.batch()
        
        schedules.forEach { schedule ->
            val scheduleDoc = db.collection("schedules").document(schedule.id)
            val scheduleData = mapOf(
                "subject" to schedule.subject,
                "section" to schedule.section,
                "teacherId" to schedule.teacherId,
                "teacherName" to schedule.teacherName,
                "startTime" to schedule.startTime,
                "endTime" to schedule.endTime,
                "day" to schedule.day,
                "room" to schedule.room,
                "createdAt" to System.currentTimeMillis(),
                "isDemo" to true
            )
            batch.set(scheduleDoc, scheduleData)
        }
        
        batch.commit()
    }
    
    private suspend fun generateDemoAttendanceData(students: List<DemoStudent>, schedules: List<DemoSchedule>) {
        val batch = db.batch()
        val calendar = Calendar.getInstance()
        val attendanceStatuses = AttendanceStatus.values()
        
        // Generate attendance for the past 30 days
        for (daysAgo in 1..30) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_MONTH, -daysAgo)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            
            // Skip weekends
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                continue
            }
            
            val dayName = when (dayOfWeek) {
                Calendar.MONDAY -> "Monday"
                Calendar.TUESDAY -> "Tuesday"
                Calendar.WEDNESDAY -> "Wednesday"
                Calendar.THURSDAY -> "Thursday"
                Calendar.FRIDAY -> "Friday"
                else -> continue
            }
            
            // Find schedules for this day
            val daySchedules = schedules.filter { it.day == dayName }
            
            daySchedules.forEach { schedule ->
                // Generate attendance for students in this section (case-insensitive)
                val sectionStudents = students.filter { it.section.equals(schedule.section, ignoreCase = true) }
                
                sectionStudents.forEach { student ->
                    // 85% chance of having an attendance record (some students might be absent)
                    if (Random.nextFloat() < 0.85f) {
                        val status = when (Random.nextFloat()) {
                            in 0.0f..0.8f -> AttendanceStatus.PRESENT
                            in 0.8f..0.9f -> AttendanceStatus.LATE
                            in 0.9f..0.95f -> AttendanceStatus.EXCUSED
                            else -> AttendanceStatus.ABSENT
                        }
                        
                        val attendanceDoc = db.collection("attendance").document()
                        val attendanceData = mapOf(
                            "userId" to student.id,
                            "studentName" to student.name,
                            "sessionId" to "${schedule.id}_${calendar.timeInMillis}",
                            "teacherId" to schedule.teacherId,
                            "scheduleId" to schedule.id,
                            "subject" to schedule.subject,
                            "section" to schedule.section,
                            "timestamp" to Timestamp(calendar.time),
                            "status" to status.name,
                            "location" to schedule.room,
                            "notes" to if (status == AttendanceStatus.LATE) "Arrived ${Random.nextInt(5, 15)} minutes late" else "",
                            "isDemo" to true
                        )
                        batch.set(attendanceDoc, attendanceData)
                    }
                }
            }
        }
        
        batch.commit()
    }
    
    suspend fun clearDemoData(): Result<String> {
        return try {
            // Clear demo users
            val demoUsers = db.collection("users")
                .whereEqualTo("isDemo", true)
                .get()
                .await()
            
            val batch = db.batch()
            demoUsers.documents.forEach { doc: com.google.firebase.firestore.DocumentSnapshot ->
                batch.delete(doc.reference)
            }
            
            // Clear demo schedules
            val demoSchedules = db.collection("schedules")
                .whereEqualTo("isDemo", true)
                .get()
                .await()
            
            demoSchedules.documents.forEach { doc: com.google.firebase.firestore.DocumentSnapshot ->
                batch.delete(doc.reference)
            }
            
            // Clear demo attendance
            val demoAttendance = db.collection("attendance")
                .whereEqualTo("isDemo", true)
                .get()
                .await()
            
            demoAttendance.documents.forEach { doc: com.google.firebase.firestore.DocumentSnapshot ->
                batch.delete(doc.reference)
            }
            
            batch.commit()
            
            Result.success("Demo data cleared successfully!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 