package com.example.attendancesystem

import androidx.lifecycle.ViewModel

class TeacherDashboardViewModel : ViewModel() {
    val manuallyEndedSessionKeys = mutableSetOf<String>()
    val hiddenAttendanceDocIds = mutableListOf<String>()
    var suppressDashboardAttendanceFromFirestore = false
}
