package com.example.attendancesystem

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.Date

class TeacherAnalyticsFragment : Fragment() {
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val teacherSubjects = mutableListOf<String>()
    private val subjectSections = mutableMapOf<String, MutableList<String>>()
    private var analyticsListener: ListenerRegistration? = null
    private var archivedAnalyticsListener: ListenerRegistration? = null
    private var activeAttendanceData = listOf<TeacherAnalyticsData>()
    private var archivedAttendanceData = listOf<TeacherAnalyticsData>()

    private lateinit var totalClassesText: TextView
    private lateinit var totalStudentsText: TextView
    private lateinit var attendanceRateText: TextView
    private lateinit var punctualityRateText: TextView
    private lateinit var bestPerformingClassText: TextView
    private lateinit var statSessions: TextView
    private lateinit var statStudents: TextView
    private lateinit var statAvgRate: TextView
    private lateinit var ringChart: CircularProgressIndicator
    private lateinit var ringPct: TextView
    private lateinit var legendOnTime: TextView
    private lateinit var legendAbsent: TextView
    private lateinit var legendLate: TextView
    private lateinit var legendHours: TextView
    private lateinit var filterSubjectValue: TextView
    private lateinit var filterSectionValue: TextView
    private lateinit var barChartContainer: LinearLayout
    private lateinit var barLabelContainer: LinearLayout
    private lateinit var classBreakdownContainer: LinearLayout

    private lateinit var timePills: List<TextView>
    private var selectedPeriodDays = 7
    private var selectedSubject = "All Subjects"
    private var selectedSection = "All Sections"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        totalClassesText = view.findViewById(R.id.totalClassesText)
        totalStudentsText = view.findViewById(R.id.totalStudentsText)
        attendanceRateText = view.findViewById(R.id.attendanceRateText)
        punctualityRateText = view.findViewById(R.id.punctualityRateText)
        bestPerformingClassText = view.findViewById(R.id.bestPerformingClassText)
        statSessions = view.findViewById(R.id.statSessions)
        statStudents = view.findViewById(R.id.statStudents)
        statAvgRate = view.findViewById(R.id.statAvgRate)
        ringChart = view.findViewById(R.id.ringChart)
        ringPct = view.findViewById(R.id.ringPct)
        legendOnTime = view.findViewById(R.id.legendOnTime)
        legendAbsent = view.findViewById(R.id.legendAbsent)
        legendLate = view.findViewById(R.id.legendLate)
        legendHours = view.findViewById(R.id.legendHours)
        filterSubjectValue = view.findViewById(R.id.filterSubjectValue)
        filterSectionValue = view.findViewById(R.id.filterSectionValue)
        barChartContainer = view.findViewById(R.id.barChartContainer)
        barLabelContainer = view.findViewById(R.id.barLabelContainer)
        classBreakdownContainer = view.findViewById(R.id.classBreakdownContainer)

        timePills = listOf(
            view.findViewById(R.id.pill7Days),
            view.findViewById(R.id.pill4Weeks),
            view.findViewById(R.id.pill12Weeks),
            view.findViewById(R.id.pillYear)
        )
        val periodDays = listOf(7, 28, 84, 365)

        timePills.forEachIndexed { i, pill ->
            pill.setOnClickListener {
                selectedPeriodDays = periodDays[i]
                selectPill(i)
                loadAnalyticsData()
            }
        }
        selectPill(0)

        view.findViewById<MaterialCardView>(R.id.btnFilterSubject).setOnClickListener { showSubjectFilter() }
        view.findViewById<MaterialCardView>(R.id.btnFilterSection).setOnClickListener { showSectionFilter() }

        loadHeaderName(view)
        loadTeacherSchedules()
    }

    private fun loadHeaderName(view: View) {
        auth.currentUser?.let { user ->
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val name = doc?.getString("name") ?: "Teacher"
                    view.findViewById<TextView>(R.id.textName)?.text = name
                    val initials = name.split(" ").filter { it.isNotBlank() }.take(2)
                        .joinToString("") { it.first().uppercaseChar().toString() }
                    view.findViewById<TextView>(R.id.textInitials)?.text = initials.ifBlank { "TC" }
                }
        }
    }

    private fun selectPill(index: Int) {
        timePills.forEachIndexed { i, pill ->
            if (i == index) {
                pill.setBackgroundResource(R.drawable.bg_teacher_day_tab_active)
                pill.setTextColor(ContextCompat.getColor(requireContext(), R.color.teacher_ds_primary_dark))
            } else {
                pill.setBackgroundColor(Color.TRANSPARENT)
                pill.setTextColor(Color.parseColor("#80FFFFFF"))
            }
        }
    }

    private fun showSubjectFilter() {
        val options = mutableListOf("All Subjects").apply { addAll(teacherSubjects) }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select subject")
            .setItems(options.toTypedArray()) { d, which ->
                selectedSubject = options[which]
                filterSubjectValue.text = if (selectedSubject == "All Subjects") "All subjects" else selectedSubject
                if (selectedSubject != "All Subjects") {
                    selectedSection = "All Sections"
                    filterSectionValue.text = "All sections"
                }
                loadAnalyticsData()
                d.dismiss()
            }.show()
    }

    private fun showSectionFilter() {
        val sections = mutableListOf("All Sections")
        if (selectedSubject == "All Subjects") {
            subjectSections.values.forEach { sections.addAll(it) }
        } else {
            subjectSections[selectedSubject]?.let { sections.addAll(it) }
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select section")
            .setItems(sections.distinct().toTypedArray()) { d, which ->
                selectedSection = sections.distinct()[which]
                filterSectionValue.text = if (selectedSection == "All Sections") "All sections" else selectedSection
                loadAnalyticsData()
                d.dismiss()
            }.show()
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
                loadAnalyticsData()
            }
            .addOnFailureListener { loadAnalyticsData() }
    }

    private fun isExcludedFromTeacherDashboard(doc: DocumentSnapshot): Boolean {
        if (doc.getBoolean("excludeFromTeacherDashboard") == true) return true
        return when (val v = doc.get("excludeFromTeacherDashboard")) {
            is Long -> v == 1L
            is String -> v.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun loadAnalyticsData() {
        val currentUser = auth.currentUser ?: return

        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, -selectedPeriodDays)
        val startDate = calendar.time

        analyticsListener?.remove()
        analyticsListener = db.collection("attendance")
            .whereEqualTo("teacherId", currentUser.uid)
            .addSnapshotListener { documents, e ->
                if (e != null) { activeAttendanceData = emptyList(); processData(startDate, endDate); return@addSnapshotListener }
                activeAttendanceData = documents?.mapNotNull { doc ->
                    if (isExcludedFromTeacherDashboard(doc)) return@mapNotNull null
                    val ts = doc.getTimestamp("timestamp")?.toDate()
                    if (ts != null && ts.after(startDate) && ts.before(endDate))
                        TeacherAnalyticsData(doc.getString("studentName") ?: "", doc.getString("subject") ?: "",
                            doc.getString("section") ?: "", doc.getString("status") ?: "PRESENT", ts)
                    else null
                } ?: emptyList()
                processData(startDate, endDate)
            }

        archivedAnalyticsListener?.remove()
        archivedAnalyticsListener = db.collection("archived_attendance")
            .whereEqualTo("teacherId", currentUser.uid)
            .addSnapshotListener { documents, e ->
                if (e != null) { archivedAttendanceData = emptyList(); processData(startDate, endDate); return@addSnapshotListener }
                archivedAttendanceData = documents?.mapNotNull { doc ->
                    if (isExcludedFromTeacherDashboard(doc)) return@mapNotNull null
                    val ts = doc.getTimestamp("timestamp")?.toDate()
                    if (ts != null && ts.after(startDate) && ts.before(endDate))
                        TeacherAnalyticsData(doc.getString("studentName") ?: "", doc.getString("subject") ?: "",
                            doc.getString("section") ?: "", doc.getString("status") ?: "PRESENT", ts)
                    else null
                } ?: emptyList()
                processData(startDate, endDate)
            }
    }

    private fun processData(startDate: Date, endDate: Date) {
        var data = activeAttendanceData + archivedAttendanceData
        data = data.filter { teacherSubjects.contains(it.subject) }
        if (selectedSubject != "All Subjects") data = data.filter { it.subject == selectedSubject }
        if (selectedSection != "All Sections") data = data.filter { it.section.equals(selectedSection, ignoreCase = true) }
        updateStatistics(data)
    }

    private fun updateStatistics(data: List<TeacherAnalyticsData>) {
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val totalRecords = data.size
        val present = data.count { it.status == "PRESENT" }
        val late = data.count { it.status == "LATE" }
        val absent = data.count { it.status == "ABSENT" || it.status == "CUTTING" || it.status == "EXCUSED" }
        val uniqueStudents = data.map { it.studentName }.distinct().size
        val uniqueSessions = data.map { "${it.subject}|${it.section}|${dateFmt.format(it.timestamp)}" }.distinct().size
        val rate = if (totalRecords > 0) (present + late).toFloat() / totalRecords * 100 else 0f
        val punctuality = if (totalRecords > 0) present.toFloat() / totalRecords * 100 else 0f

        statSessions.text = uniqueSessions.toString()
        statStudents.text = uniqueStudents.toString()
        statAvgRate.text = String.format("%.0f%%", rate)

        ringChart.progress = rate.toInt().coerceIn(0, 100)
        ringPct.text = String.format("%.0f%%", rate)

        legendOnTime.text = "● On time: $present"
        legendAbsent.text = "● Absences: $absent"
        legendLate.text = "● Late: $late"
        legendHours.text = "● Records: $totalRecords"

        totalClassesText.text = uniqueSessions.toString()
        totalStudentsText.text = uniqueStudents.toString()
        attendanceRateText.text = String.format("%.1f%%", rate)
        punctualityRateText.text = String.format("%.1f%%", punctuality)

        val bestClass = data.groupBy { "${it.subject} - ${it.section}" }
            .filter { it.value.isNotEmpty() }
            .map { (name, records) ->
                val classRate = records.count { it.status == "PRESENT" || it.status == "LATE" }.toFloat() / records.size * 100
                name to classRate
            }.maxByOrNull { it.second }
        bestPerformingClassText.text = bestClass?.first ?: "N/A"

        buildBarChart(data)
        buildClassBreakdown(data)
    }

    private fun buildBarChart(data: List<TeacherAnalyticsData>) {
        barChartContainer.removeAllViews()
        barLabelContainer.removeAllViews()

        val cal = Calendar.getInstance()
        val monthData = mutableMapOf<Int, Int>()
        for (i in 5 downTo 0) {
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
            monthData[c.get(Calendar.MONTH)] = 0
        }
        data.forEach { d ->
            cal.time = d.timestamp
            val m = cal.get(Calendar.MONTH)
            if (monthData.containsKey(m)) monthData[m] = monthData[m]!! + 1
        }

        val maxVal = monthData.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val colors = arrayOf("#1D9E75", "#5DCAA5", "#C0DD97")

        monthData.forEach { (month, count) ->
            val heightPct = count.toFloat() / maxVal
            val barColor = when {
                heightPct > 0.66f -> colors[0]
                heightPct > 0.33f -> colors[1]
                else -> colors[2]
            }

            val bar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, (70 * resources.displayMetrics.density * heightPct.coerceAtLeast(0.05f)).toInt(), 1f).apply {
                    marginEnd = (3 * resources.displayMetrics.density).toInt()
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(barColor))
                    cornerRadii = floatArrayOf(10f, 10f, 10f, 10f, 0f, 0f, 0f, 0f)
                }
            }
            barChartContainer.addView(bar)

            val label = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (3 * resources.displayMetrics.density).toInt()
                }
                text = String.format("%02d", month + 1)
                textSize = 9f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.teacher_ds_text_secondary))
                gravity = Gravity.CENTER
            }
            barLabelContainer.addView(label)
        }
    }

    private fun buildClassBreakdown(data: List<TeacherAnalyticsData>) {
        classBreakdownContainer.removeAllViews()
        val subjectColors = listOf("#1D9E75", "#378ADD", "#D85A30", "#BA7517", "#7F77DD", "#D4537E")
        val colorMap = mutableMapOf<String, String>()

        val grouped = data.groupBy { it.subject }
        grouped.keys.forEachIndexed { i, subj ->
            colorMap[subj] = subjectColors[i % subjectColors.size]
        }

        grouped.forEach { (subject, records) ->
            val rate = records.count { it.status == "PRESENT" || it.status == "LATE" }.toFloat() / records.size * 100
            val students = records.map { it.studentName }.distinct().size
            val section = records.firstOrNull()?.section ?: ""
            val color = Color.parseColor(colorMap[subject] ?: "#1D9E75")

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(8), 0, dpToPx(8))
            }

            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)).apply { marginEnd = dpToPx(10) }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            }
            row.addView(dot)

            val info = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val nameView = TextView(requireContext()).apply {
                text = subject; textSize = 11f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.teacher_ds_text_primary))
            }
            val secView = TextView(requireContext()).apply {
                text = "$section · $students students"; textSize = 9f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.teacher_ds_text_secondary))
            }
            info.addView(nameView); info.addView(secView)
            row.addView(info)

            val badge = TextView(requireContext()).apply {
                text = String.format("%.0f%%", rate); textSize = 11f
                setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
                val bgColor: Int; val txtColor: Int
                when {
                    rate >= 80 -> { bgColor = Color.parseColor("#E1F5EE"); txtColor = Color.parseColor("#0F6E56") }
                    rate >= 60 -> { bgColor = Color.parseColor("#FAEEDA"); txtColor = Color.parseColor("#854F0B") }
                    else -> { bgColor = Color.parseColor("#FCEBEB"); txtColor = Color.parseColor("#A32D2D") }
                }
                background = GradientDrawable().apply { setColor(bgColor); cornerRadius = 40f }
                setTextColor(txtColor)
            }
            row.addView(badge)
            classBreakdownContainer.addView(row)

            val divider = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.teacher_ds_border_default))
            }
            classBreakdownContainer.addView(divider)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        analyticsListener?.remove(); analyticsListener = null
        archivedAnalyticsListener?.remove(); archivedAnalyticsListener = null
    }
}

data class TeacherAnalyticsData(
    val studentName: String, val subject: String, val section: String,
    val status: String, val timestamp: Date
)
