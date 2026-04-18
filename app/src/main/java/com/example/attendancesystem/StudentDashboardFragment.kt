package com.example.attendancesystem

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class StudentDashboardFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var textWelcomeStudent: TextView
    private lateinit var textName: TextView
    private lateinit var textCourse: TextView
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var textPresentCount: TextView
    private lateinit var textAbsentCount: TextView
    private lateinit var textLateCount: TextView
    private lateinit var textAttendedHours: TextView
    private lateinit var textAttendancePercent: TextView
    private lateinit var attendanceRing: CircularProgressIndicator
    private lateinit var filter7Days: MaterialButton
    private lateinit var filter4Weeks: MaterialButton
    private lateinit var filter12Weeks: MaterialButton
    private lateinit var filterYear: MaterialButton

    private lateinit var barViews: List<View>
    private lateinit var monthLabels: List<TextView>

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var studentName: String = ""
    private var selectedRange = AttendanceRange.YEAR

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_student_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        loadUserData()
        loadAttendanceStats()
        setupSwipeRefresh()
        setupRangeButtons()
    }

    private fun initializeViews(view: View) {
        try {
            swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
            textWelcomeStudent = view.findViewById(R.id.textWelcomeStudent)
            textName = view.findViewById(R.id.textName)
            textCourse = view.findViewById(R.id.textCourse)
            imageProfilePic = view.findViewById(R.id.imageProfilePic)
            textInitials = view.findViewById(R.id.textInitials)
            textPresentCount = view.findViewById(R.id.textPresentCount)
            textAbsentCount = view.findViewById(R.id.textAbsentCount)
            textLateCount = view.findViewById(R.id.textLateCount)
            textAttendedHours = view.findViewById(R.id.textAttendedHours)
            textAttendancePercent = view.findViewById(R.id.textAttendancePercent)
            attendanceRing = view.findViewById(R.id.attendanceRing)
            filter7Days = view.findViewById(R.id.filter7Days)
            filter4Weeks = view.findViewById(R.id.filter4Weeks)
            filter12Weeks = view.findViewById(R.id.filter12Weeks)
            filterYear = view.findViewById(R.id.filterYear)

            barViews = listOf(
                view.findViewById(R.id.bar0),
                view.findViewById(R.id.bar1),
                view.findViewById(R.id.bar2),
                view.findViewById(R.id.bar3),
                view.findViewById(R.id.bar4),
                view.findViewById(R.id.bar5),
                view.findViewById(R.id.bar6),
                view.findViewById(R.id.bar7),
                view.findViewById(R.id.bar8),
                view.findViewById(R.id.bar9),
                view.findViewById(R.id.bar10),
                view.findViewById(R.id.bar11)
            )
            monthLabels = listOf(
                view.findViewById(R.id.month0),
                view.findViewById(R.id.month1),
                view.findViewById(R.id.month2),
                view.findViewById(R.id.month3),
                view.findViewById(R.id.month4),
                view.findViewById(R.id.month5),
                view.findViewById(R.id.month6),
                view.findViewById(R.id.month7),
                view.findViewById(R.id.month8),
                view.findViewById(R.id.month9),
                view.findViewById(R.id.month10),
                view.findViewById(R.id.month11)
            )
        } catch (e: Exception) {
            android.util.Log.e("StudentDashboard", "Error initializing views", e)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadUserData()
            loadAttendanceStats()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun setupRangeButtons() {
        filter7Days.setOnClickListener { selectRange(AttendanceRange.DAYS_7) }
        filter4Weeks.setOnClickListener { selectRange(AttendanceRange.WEEKS_4) }
        filter12Weeks.setOnClickListener { selectRange(AttendanceRange.WEEKS_12) }
        filterYear.setOnClickListener { selectRange(AttendanceRange.YEAR) }
        updateRangeButtonState()
    }

    private fun selectRange(range: AttendanceRange) {
        if (selectedRange == range) return
        selectedRange = range
        updateRangeButtonState()
        loadAttendanceStats()
    }

    private fun updateRangeButtonState() {
        val selectedBg = ContextCompat.getColor(requireContext(), R.color.primary)
        val selectedText = ContextCompat.getColor(requireContext(), R.color.on_primary)
        val defaultText = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        val transparent = ContextCompat.getColor(requireContext(), android.R.color.transparent)

        val buttons = listOf(
            filter7Days to AttendanceRange.DAYS_7,
            filter4Weeks to AttendanceRange.WEEKS_4,
            filter12Weeks to AttendanceRange.WEEKS_12,
            filterYear to AttendanceRange.YEAR
        )
        for ((button, range) in buttons) {
            val selected = range == selectedRange
            button.setBackgroundColor(if (selected) selectedBg else transparent)
            button.setTextColor(if (selected) selectedText else defaultText)
        }
    }

    private fun loadAttendanceStats() {
        val currentUser = auth.currentUser ?: return

        val calendar = Calendar.getInstance()
        when (selectedRange) {
            AttendanceRange.DAYS_7 -> calendar.add(Calendar.DAY_OF_YEAR, -7)
            AttendanceRange.WEEKS_4 -> calendar.add(Calendar.DAY_OF_YEAR, -28)
            AttendanceRange.WEEKS_12 -> calendar.add(Calendar.DAY_OF_YEAR, -84)
            AttendanceRange.YEAR -> calendar.add(Calendar.DAY_OF_YEAR, -365)
        }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val rangeStart = com.google.firebase.Timestamp(calendar.time)

        val uid = currentUser.uid
        db.collection("attendance")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { activeSnap ->
                db.collection("archived_attendance")
                    .whereEqualTo("userId", uid)
                    .get()
                    .addOnSuccessListener { archSnap ->
                        if (!isAdded) return@addOnSuccessListener
                        val merged = (activeSnap.documents + archSnap.documents).distinctBy { it.id }
                        val docsInRange = merged.filter { doc ->
                            val ts = doc.getTimestamp("timestamp") ?: return@filter false
                            ts >= rangeStart
                        }
                        // #region agent log
                        AgentDebugLog.log(
                            "StudentDashboardFragment:loadAttendanceStats",
                            "merged active+archived",
                            "H4",
                            mapOf(
                                "activeTotal" to activeSnap.documents.size,
                                "archivedTotal" to archSnap.documents.size,
                                "docsInRange" to docsInRange.size
                            )
                        )
                        // #endregion
                        applyAttendanceRangeStats(docsInRange)
                        loadMonthlyTrendFromMergedDocs(merged)
                    }
                    .addOnFailureListener {
                        if (!isAdded) return@addOnFailureListener
                        val merged = activeSnap.documents
                        val docsInRange = merged.filter { doc ->
                            val ts = doc.getTimestamp("timestamp") ?: return@filter false
                            ts >= rangeStart
                        }
                        applyAttendanceRangeStats(docsInRange)
                        loadMonthlyTrendFromMergedDocs(merged)
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("StudentDashboard", "Error loading stats: ${e.message}", e)
                textPresentCount.text = "0 On Time"
                textLateCount.text = "0 Lates"
                textAbsentCount.text = "0 Absences"
                textAttendedHours.text = "0 Classes Attended"
                textAttendancePercent.text = "0%"
                attendanceRing.setProgressCompat(0, true)
                renderEmptyTrend()
            }
    }

    private fun applyAttendanceRangeStats(docsInRange: List<com.google.firebase.firestore.DocumentSnapshot>) {
        var onTimeCount = 0
        var absentCount = 0
        var lateCount = 0
        var attendedCount = 0

        for (doc in docsInRange) {
            val status = doc.getString("status")?.trim()?.uppercase() ?: "PRESENT"
            when (status) {
                "PRESENT" -> {
                    onTimeCount++
                    attendedCount++
                }
                "ABSENT" -> absentCount++
                "LATE" -> {
                    lateCount++
                    attendedCount++
                }
                "EXCUSED" -> attendedCount++
                "CUTTING" -> absentCount++
            }
        }

        val totalCount = attendedCount + absentCount
        val percentage = if (totalCount == 0) 0 else ((attendedCount * 100f) / totalCount).toInt()

        textPresentCount.text = "$onTimeCount On Time"
        textLateCount.text = "$lateCount Lates"
        textAbsentCount.text = "$absentCount Absences"
        textAttendedHours.text = "$attendedCount Classes Attended"
        textAttendancePercent.text = "$percentage%"
        attendanceRing.max = 100
        attendanceRing.setProgressCompat(percentage, true)
    }

    private fun loadMonthlyTrendFromMergedDocs(merged: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -11)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val start = com.google.firebase.Timestamp(calendar.time)
        val docsInRange = merged.filter { doc ->
            val ts = doc.getTimestamp("timestamp") ?: return@filter false
            ts >= start
        }
        renderMonthlyTrendBars(docsInRange)
    }

    private fun renderMonthlyTrendBars(docsInRange: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val formatter = SimpleDateFormat("MMM", Locale.getDefault())
        val nowCalendar = Calendar.getInstance()
        val monthKeys = mutableListOf<String>()
        repeat(12) { index ->
            val temp = Calendar.getInstance()
            temp.add(Calendar.MONTH, index - 11)
            monthKeys.add("${temp.get(Calendar.YEAR)}-${temp.get(Calendar.MONTH)}")
            monthLabels[index].text = formatter.format(temp.time)
        }

        val monthTotals = monthKeys.associateWith { 0 }.toMutableMap()
        val monthAttended = monthKeys.associateWith { 0 }.toMutableMap()
        for (doc in docsInRange) {
            val date = doc.getTimestamp("timestamp")?.toDate() ?: continue
            nowCalendar.time = date
            val key = "${nowCalendar.get(Calendar.YEAR)}-${nowCalendar.get(Calendar.MONTH)}"
            if (!monthTotals.containsKey(key)) continue
            val status = doc.getString("status")?.trim()?.uppercase() ?: "PRESENT"
            monthTotals[key] = (monthTotals[key] ?: 0) + 1
            if (status == "PRESENT" || status == "LATE" || status == "EXCUSED") {
                monthAttended[key] = (monthAttended[key] ?: 0) + 1
            }
        }

        monthKeys.forEachIndexed { index, key ->
            val total = monthTotals[key] ?: 0
            val attended = monthAttended[key] ?: 0
            val percent = if (total == 0) 0 else (attended * 100 / total)
            updateBarHeight(barViews[index], percent)
        }
    }

    private fun renderEmptyTrend() {
        monthLabels.forEach { it.text = "--" }
        barViews.forEach { updateBarHeight(it, 0) }
    }

    private fun updateBarHeight(bar: View, percent: Int) {
        val minHeight = dpToPx(22)
        val maxHeight = dpToPx(150)
        val targetHeight = minHeight + ((maxHeight - minHeight) * percent / 100)
        val params = bar.layoutParams
        params.height = targetHeight
        bar.layoutParams = params
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            try {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            } catch (e: Exception) {
                android.util.Log.e("StudentDashboard", "Error redirecting to login: ${e.message}")
            }
            return
        }

        db.collection("users").document(currentUser.uid)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    studentName = snapshot.getString("name") ?: "Student"
                    textWelcomeStudent.text = getGreeting()
                    val studentSection = snapshot.getString("section") ?: "Section"
                    textName.text = studentName
                    textCourse.text = studentSection.uppercase()
                    ProfilePictureManager.getInstance().loadProfilePicture(requireContext(), imageProfilePic, textInitials, studentName, "ST")
                }
            }
    }

    private fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    private enum class AttendanceRange {
        DAYS_7, WEEKS_4, WEEKS_12, YEAR
    }
}
