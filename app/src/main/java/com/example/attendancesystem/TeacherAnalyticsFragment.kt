package com.example.attendancesystem

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Date

class TeacherAnalyticsFragment : Fragment() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val teacherSubjects = mutableListOf<String>()
    private val subjectSections = mutableMapOf<String, MutableList<String>>()
    private var analyticsListener: ListenerRegistration? = null

    private lateinit var totalClassesText: TextView
    private lateinit var avgAttendanceText: TextView
    private lateinit var totalStudentsText: TextView
    private lateinit var bestPerformingClassText: TextView
    private lateinit var attendanceRateText: TextView
    private lateinit var punctualityRateText: TextView

    private lateinit var periodSpinner: Spinner
    private lateinit var subjectSpinner: Spinner
    private lateinit var sectionSpinner: Spinner

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        totalClassesText = view.findViewById(R.id.totalClassesText)
        avgAttendanceText = view.findViewById(R.id.avgAttendanceText)
        totalStudentsText = view.findViewById(R.id.totalStudentsText)
        bestPerformingClassText = view.findViewById(R.id.bestPerformingClassText)
        attendanceRateText = view.findViewById(R.id.attendanceRateText)
        punctualityRateText = view.findViewById(R.id.punctualityRateText)
        periodSpinner = view.findViewById(R.id.periodSpinner)
        subjectSpinner = view.findViewById(R.id.subjectSpinner)
        sectionSpinner = view.findViewById(R.id.sectionSpinner)

        loadTeacherSchedules()
    }

    private fun loadTeacherSchedules() {
        val currentUser = auth.currentUser ?: return
        db.collection("schedules")
            .whereEqualTo("teacherId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                teacherSubjects.clear()
                subjectSections.clear()
                for (document in documents) {
                    val subject = document.getString("subject") ?: continue
                    val section = document.getString("section") ?: continue
                    if (!teacherSubjects.contains(subject)) teacherSubjects.add(subject)
                    if (!subjectSections.containsKey(subject)) subjectSections[subject] = mutableListOf()
                    if (!subjectSections[subject]!!.contains(section)) subjectSections[subject]!!.add(section)
                }
                teacherSubjects.sort()
                subjectSections.values.forEach { it.sort() }
                setupSpinners()
            }
            .addOnFailureListener { setupSpinners() }
    }

    private fun setupSpinners() {
        val periods = listOf("Last 7 Days", "Last 30 Days", "Last 3 Months", "All Time")
        val periodAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, periods)
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        periodSpinner.adapter = periodAdapter

        val subjects = mutableListOf("All Subjects").apply { addAll(teacherSubjects) }
        val subjectAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, subjects)
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = subjectAdapter

        updateSectionSpinner("All Subjects")

        val periodListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { loadAnalyticsData() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val subjectListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSubject = parent?.getItemAtPosition(position).toString()
                updateSectionSpinner(selectedSubject)
                loadAnalyticsData()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val sectionListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { loadAnalyticsData() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        periodSpinner.onItemSelectedListener = periodListener
        subjectSpinner.onItemSelectedListener = subjectListener
        sectionSpinner.onItemSelectedListener = sectionListener

        loadAnalyticsData()
    }

    private fun updateSectionSpinner(selectedSubject: String) {
        val sections = mutableListOf("All Sections")
        if (selectedSubject == "All Subjects") {
            val allSections = mutableSetOf<String>()
            subjectSections.values.forEach { allSections.addAll(it) }
            sections.addAll(allSections.sorted())
        } else {
            subjectSections[selectedSubject]?.let { sections.addAll(it) }
        }
        val sectionAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sections)
        sectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sectionSpinner.adapter = sectionAdapter
    }

    private fun loadAnalyticsData() {
        try {
            val selectedPeriod = periodSpinner.selectedItem?.toString() ?: "Last 30 Days"
            val selectedSubject = subjectSpinner.selectedItem?.toString() ?: "All Subjects"
            val selectedSection = sectionSpinner.selectedItem?.toString() ?: "All Sections"

            val calendar = Calendar.getInstance()
            val endDate = calendar.time
            when (selectedPeriod) {
                "Last 7 Days" -> calendar.add(Calendar.DAY_OF_YEAR, -7)
                "Last 30 Days" -> calendar.add(Calendar.DAY_OF_YEAR, -30)
                "Last 3 Months" -> calendar.add(Calendar.MONTH, -3)
                "All Time" -> calendar.add(Calendar.YEAR, -1)
            }
            val startDate = calendar.time

            // Server query: range + orderBy on timestamp; attach realtime listener
            val query: Query = db.collection("attendance")
                .whereGreaterThanOrEqualTo("timestamp", startDate)
                .whereLessThanOrEqualTo("timestamp", endDate)
                .orderBy("timestamp")

            analyticsListener?.remove()
            analyticsListener = query.addSnapshotListener { documents, e ->
                if (e != null) {
                    android.widget.Toast.makeText(requireContext(), "Analytics listen failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                var data = documents?.map { doc ->
                    TeacherAnalyticsData(
                        studentName = doc.getString("studentName") ?: "",
                        subject = doc.getString("subject") ?: "",
                        section = doc.getString("section") ?: "",
                        status = doc.getString("status") ?: "PRESENT",
                        timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                    )
                } ?: emptyList()

                // Filter to classes the teacher actually owns
                data = data.filter { d ->
                    teacherSubjects.contains(d.subject) && subjectSections[d.subject]?.contains(d.section) == true
                }

                // Apply UI filters in memory to avoid extra indexes
                if (selectedSubject != "All Subjects") {
                    data = data.filter { it.subject == selectedSubject }
                }
                if (selectedSection != "All Sections") {
                    data = data.filter { it.section.equals(selectedSection, ignoreCase = true) }
                }

                updateStatistics(data)
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "Analytics error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        analyticsListener?.remove()
        analyticsListener = null
    }

    private fun updateStatistics(attendanceData: List<TeacherAnalyticsData>) {
        val totalRecords = attendanceData.size
        val uniqueStudents = attendanceData.map { it.studentName }.distinct().size
        val presentCount = attendanceData.count { it.status == "PRESENT" }
        val lateCount = attendanceData.count { it.status == "LATE" }
        val attendanceRate = if (totalRecords > 0) (presentCount + lateCount).toFloat() / totalRecords * 100 else 0f
        val punctualityRate = if (totalRecords > 0) presentCount.toFloat() / totalRecords * 100 else 0f

        totalClassesText.text = totalRecords.toString()
        totalStudentsText.text = uniqueStudents.toString()
        attendanceRateText.text = String.format("%.1f%%", attendanceRate)
        punctualityRateText.text = String.format("%.1f%%", punctualityRate)

        val bestClass = attendanceData.groupBy { "${it.subject} - ${it.section}" }
            .map { (className, records) ->
                val classAttendanceRate = records.count { it.status == "PRESENT" }.toFloat() / records.size * 100
                className to classAttendanceRate
            }
            .maxByOrNull { it.second }
        bestPerformingClassText.text = bestClass?.first ?: "N/A"
        avgAttendanceText.text = String.format("%.1f%%", bestClass?.second ?: 0f)
    }
}

data class TeacherAnalyticsData(
    val studentName: String,
    val subject: String,
    val section: String,
    val status: String,
    val timestamp: Date
)
