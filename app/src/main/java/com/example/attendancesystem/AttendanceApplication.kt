package com.example.attendancesystem

import android.app.Application
import com.example.attendancesystem.utils.ThemeManager

class AttendanceApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize theme system
        val themeManager = ThemeManager.getInstance(this)
        themeManager.initializeTheme()
    }
}
