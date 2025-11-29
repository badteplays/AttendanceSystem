package com.example.attendancesystem.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.attendancesystem.models.AttendanceStatus
import com.google.firebase.Timestamp
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class DataExporter(private val context: Context) {

    companion object {
        private const val CSV_HEADER = "Date,Time,Student Name,Subject,Section,Status,Teacher,Notes"
        private const val EXPORT_FOLDER = "AttendanceReports"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    data class AttendanceRecord(
        val studentName: String,
        val subject: String,
        val section: String,
        val status: AttendanceStatus,
        val timestamp: Date,
        val teacherName: String,
        val notes: String
    )


    fun exportToCSV(
        records: List<AttendanceRecord>,
        fileName: String? = null,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), EXPORT_FOLDER)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val finalFileName = fileName ?: "attendance_report_${fileNameFormat.format(Date())}.csv"
            val file = File(exportDir, finalFileName)

            FileWriter(file).use { writer ->

                writer.append(CSV_HEADER)
                writer.append("\n")

                records.forEach { record ->
                    val row = buildString {
                        append(escapeCSV(dateFormat.format(record.timestamp))).append(",")
                        append(escapeCSV(timeFormat.format(record.timestamp))).append(",")
                        append(escapeCSV(record.studentName)).append(",")
                        append(escapeCSV(record.subject)).append(",")
                        append(escapeCSV(record.section)).append(",")
                        append(escapeCSV(record.status.name)).append(",")
                        append(escapeCSV(record.teacherName)).append(",")
                        append(escapeCSV(record.notes))
                    }
                    writer.append(row)
                    writer.append("\n")
                }

                writer.flush()
            }

            onSuccess(file)
        } catch (e: Exception) {
            onError("Failed to export data: ${e.message}")
        }
    }


    fun exportSummaryToCSV(
        records: List<AttendanceRecord>,
        fileName: String? = null,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), EXPORT_FOLDER)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val finalFileName = fileName ?: "attendance_summary_${fileNameFormat.format(Date())}.csv"
            val file = File(exportDir, finalFileName)

            val studentSummary = records.groupBy { it.studentName }
                .map { (studentName, studentRecords) ->
                    val totalClasses = studentRecords.size
                    val presentCount = studentRecords.count { it.status == AttendanceStatus.PRESENT }
                    val lateCount = studentRecords.count { it.status == AttendanceStatus.LATE }
                    val absentCount = studentRecords.count { it.status == AttendanceStatus.ABSENT }
                    val excusedCount = studentRecords.count { it.status == AttendanceStatus.EXCUSED }
                    val attendanceRate = if (totalClasses > 0) (presentCount + lateCount).toFloat() / totalClasses * 100 else 0f

                    StudentSummary(
                        studentName = studentName,
                        totalClasses = totalClasses,
                        presentCount = presentCount,
                        lateCount = lateCount,
                        absentCount = absentCount,
                        excusedCount = excusedCount,
                        attendanceRate = attendanceRate
                    )
                }

            FileWriter(file).use { writer ->

                writer.append("Student Name,Total Classes,Present,Late,Absent,Excused,Attendance Rate (%)")
                writer.append("\n")

                studentSummary.forEach { summary ->
                    val row = buildString {
                        append(escapeCSV(summary.studentName)).append(",")
                        append(summary.totalClasses).append(",")
                        append(summary.presentCount).append(",")
                        append(summary.lateCount).append(",")
                        append(summary.absentCount).append(",")
                        append(summary.excusedCount).append(",")
                        append(String.format("%.2f", summary.attendanceRate))
                    }
                    writer.append(row)
                    writer.append("\n")
                }

                writer.flush()
            }

            onSuccess(file)
        } catch (e: Exception) {
            onError("Failed to export summary: ${e.message}")
        }
    }


    fun exportClassStatsToCSV(
        records: List<AttendanceRecord>,
        fileName: String? = null,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), EXPORT_FOLDER)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val finalFileName = fileName ?: "class_statistics_${fileNameFormat.format(Date())}.csv"
            val file = File(exportDir, finalFileName)

            val classStats = records.groupBy { "${it.subject} - ${it.section}" }
                .map { (className, classRecords) ->
                    val totalSessions = classRecords.map { dateFormat.format(it.timestamp) }.distinct().size
                    val totalAttendance = classRecords.size
                    val presentCount = classRecords.count { it.status == AttendanceStatus.PRESENT }
                    val lateCount = classRecords.count { it.status == AttendanceStatus.LATE }
                    val absentCount = classRecords.count { it.status == AttendanceStatus.ABSENT }
                    val excusedCount = classRecords.count { it.status == AttendanceStatus.EXCUSED }
                    val attendanceRate = if (totalAttendance > 0) (presentCount + lateCount).toFloat() / totalAttendance * 100 else 0f
                    val averageStudentsPerSession = if (totalSessions > 0) totalAttendance.toFloat() / totalSessions else 0f

                    ClassStats(
                        className = className,
                        totalSessions = totalSessions,
                        totalAttendance = totalAttendance,
                        presentCount = presentCount,
                        lateCount = lateCount,
                        absentCount = absentCount,
                        excusedCount = excusedCount,
                        attendanceRate = attendanceRate,
                        averageStudentsPerSession = averageStudentsPerSession
                    )
                }

            FileWriter(file).use { writer ->

                writer.append("Class,Total Sessions,Total Attendance Records,Present,Late,Absent,Excused,Attendance Rate (%),Avg Students/Session")
                writer.append("\n")

                classStats.forEach { stats ->
                    val row = buildString {
                        append(escapeCSV(stats.className)).append(",")
                        append(stats.totalSessions).append(",")
                        append(stats.totalAttendance).append(",")
                        append(stats.presentCount).append(",")
                        append(stats.lateCount).append(",")
                        append(stats.absentCount).append(",")
                        append(stats.excusedCount).append(",")
                        append(String.format("%.2f", stats.attendanceRate)).append(",")
                        append(String.format("%.2f", stats.averageStudentsPerSession))
                    }
                    writer.append(row)
                    writer.append("\n")
                }

                writer.flush()
            }

            onSuccess(file)
        } catch (e: Exception) {
            onError("Failed to export class statistics: ${e.message}")
        }
    }


    fun shareFile(file: File, title: String = "Share Attendance Report") {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Attendance report generated from Attendance System")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, title)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            throw Exception("Failed to share file: ${e.message}")
        }
    }


    fun getExportedFiles(): List<File> {
        val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), EXPORT_FOLDER)
        return if (exportDir.exists()) {
            exportDir.listFiles { file -> file.extension == "csv" }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }


    fun deleteExportedFile(file: File): Boolean {
        return file.exists() && file.delete()
    }


    private fun escapeCSV(value: String): String {
        val trimmedValue = value.trim()
        return if (trimmedValue.contains(",") || trimmedValue.contains("\"") || trimmedValue.contains("\n")) {
            "\"${trimmedValue.replace("\"", "\"\"")}\""
        } else {
            trimmedValue
        }
    }

    data class StudentSummary(
        val studentName: String,
        val totalClasses: Int,
        val presentCount: Int,
        val lateCount: Int,
        val absentCount: Int,
        val excusedCount: Int,
        val attendanceRate: Float
    )

    data class ClassStats(
        val className: String,
        val totalSessions: Int,
        val totalAttendance: Int,
        val presentCount: Int,
        val lateCount: Int,
        val absentCount: Int,
        val excusedCount: Int,
        val attendanceRate: Float,
        val averageStudentsPerSession: Float
    )
}