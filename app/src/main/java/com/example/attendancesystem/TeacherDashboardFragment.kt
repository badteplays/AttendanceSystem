package com.example.attendancesystem

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.attendancesystem.adapters.TeacherAttendanceAdapter
import com.example.attendancesystem.models.AttendanceDocSource
import com.example.attendancesystem.models.StudentItem
import com.example.attendancesystem.models.TeacherAttendanceItem
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TeacherDashboardFragment : Fragment() {
    companion object {
        private const val RECENT_ATTENDANCE_LIMIT = 100
        private const val TAG = "TeacherDashboard"
        private const val EXCLUDE_FROM_TEACHER_DASHBOARD = "excludeFromTeacherDashboard"
        private const val ROSTER_ABSENT_LAST_MINUTES = 10
    }

    private val rosterAbsentWindowMs = ROSTER_ABSENT_LAST_MINUTES * 60 * 1000L
    private lateinit var imageProfilePic: ImageView
    private lateinit var textInitials: TextView
    private lateinit var textGreeting: TextView
    private lateinit var textName: TextView
    private lateinit var textDepartment: TextView
    private lateinit var textCurrentClass: TextView
    private lateinit var badgeLive: TextView
    private lateinit var textQrExpiry: TextView
    private lateinit var textQrSessionMeta: TextView
    private lateinit var textQrLiveIndicator: TextView
    private lateinit var buttonShowQr: View
    private lateinit var buttonRenewQr: View
    private lateinit var buttonRefreshAttendance: View
    private lateinit var buttonManualAdd: View
    private lateinit var buttonAnalytics: View
    private lateinit var buttonEndClass: View
    private lateinit var attendanceRecyclerView: RecyclerView
    private lateinit var textViewAll: TextView
    private lateinit var buttonAddManuallyInline: View
    private lateinit var textPresentCount: TextView
    private lateinit var textAbsentCount: TextView
    private lateinit var textLateCount: TextView
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var attendanceListener: ListenerRegistration? = null
    private var archivedAttendanceListener: ListenerRegistration? = null
    private var lastActiveAttendanceDocs: List<DocumentSnapshot> = emptyList()
    private var lastArchivedAttendanceDocs: List<DocumentSnapshot> = emptyList()
    private var listeningScheduleId: String? = null
    private var listeningSubject: String? = null
    private var currentScheduleEndTime: String? = null
    private val classEndHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var classEndRunnable: Runnable? = null
    private var attendanceAdapter: TeacherAttendanceAdapter? = null
    private val attendanceList = mutableListOf<TeacherAttendanceItem>()
    private val sharedDashboard: TeacherDashboardViewModel by activityViewModels()
    private val pendingHiddenAttendanceIds get() = sharedDashboard.hiddenAttendanceDocIds
    private var currentScheduleSection: String? = null
    private var currentScheduleStartTime: String? = null
    private var rosterStudentIds: Set<String> = emptySet()
    private var rosterAbsentTicker: Runnable? = null
    private var loadRecentAttendanceSeq = 0

    private data class SessionDayBounds(
        val localDayStart: com.google.firebase.Timestamp,
        val localDayEnd: com.google.firebase.Timestamp,
        val extendedDayStart: com.google.firebase.Timestamp,
        val extendedDayEnd: com.google.firebase.Timestamp
    )

    private fun endedSessionKeyForToday(scheduleId: String): String {
        val c = java.util.Calendar.getInstance()
        return "${scheduleId}_${c.get(java.util.Calendar.YEAR)}_${c.get(java.util.Calendar.DAY_OF_YEAR)}"
    }

    private fun hasManualEndForToday(): Boolean {
        val c = java.util.Calendar.getInstance()
        val y = c.get(java.util.Calendar.YEAR)
        val doy = c.get(java.util.Calendar.DAY_OF_YEAR)
        val suffix = "_${y}_$doy"
        return sharedDashboard.manuallyEndedSessionKeys.any { it.endsWith(suffix) }
    }

    private fun computeSessionDayBounds(): SessionDayBounds {
        val dayStartCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val localDayStart = com.google.firebase.Timestamp(dayStartCal.time)
        val dayEndCal = (dayStartCal.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val localDayEnd = com.google.firebase.Timestamp(dayEndCal.time)
        val extStartCal = (dayStartCal.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.HOUR_OF_DAY, -2)
        }
        val extEndCal = (dayEndCal.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.HOUR_OF_DAY, 2)
        }
        return SessionDayBounds(
            localDayStart,
            localDayEnd,
            com.google.firebase.Timestamp(extStartCal.time),
            com.google.firebase.Timestamp(extEndCal.time)
        )
    }

    private fun docExcludedFromTeacherDashboard(doc: DocumentSnapshot): Boolean {
        if (doc.getBoolean(EXCLUDE_FROM_TEACHER_DASHBOARD) == true) return true
        return when (val v = doc.get(EXCLUDE_FROM_TEACHER_DASHBOARD)) {
            is Long -> v == 1L
            is String -> v.equals("true", ignoreCase = true)
            else -> false
        }
    }

    private fun normalizeScheduleIdFromSnapshot(snap: DocumentSnapshot): String? {
        return when (val s = snap.get("scheduleId")) {
            is String -> s.ifBlank { null }
            is Long -> s.toString()
            is Int -> s.toString()
            is Double -> if (s.isNaN()) null else s.toLong().toString()
            else -> null
        }
    }

    private fun isScheduleManuallyEndedTodayForDoc(doc: DocumentSnapshot): Boolean {
        val sid = normalizeScheduleIdFromSnapshot(doc) ?: doc.getString("scheduleId") ?: return false
        return endedSessionKeyForToday(sid) in sharedDashboard.manuallyEndedSessionKeys
    }

    private fun mergePayloadHideFromDashboard(
        snap: DocumentSnapshot,
        scheduleIdFallback: String?
    ): Map<String, Any>? {
        val uid = auth.currentUser?.uid ?: return null
        val sid = scheduleIdFallback?.takeIf { it.isNotBlank() }
            ?: normalizeScheduleIdFromSnapshot(snap)
        if (sid.isNullOrBlank()) return null
        return mapOf(
            EXCLUDE_FROM_TEACHER_DASHBOARD to true,
            "excludedFromDashboardAt" to System.currentTimeMillis(),
            "teacherId" to uid,
            "scheduleId" to sid
        )
    }

    private fun refreshAttendanceMergeFromServer(scheduleId: String, subject: String) {
        val uid = auth.currentUser?.uid ?: return
        if (listeningScheduleId != scheduleId) {
            // #region agent log
            AgentDebugLog.log(
                "TeacherDashboardFragment:refreshAttendanceMergeFromServer",
                "early return listening mismatch",
                "H3",
                mapOf(
                    "runId" to "pre",
                    "scheduleId" to scheduleId,
                    "listeningScheduleId" to (listeningScheduleId ?: "")
                )
            )
            // #endregion
            return
        }
        val b = computeSessionDayBounds()
        db.collection("attendance")
            .whereEqualTo("teacherId", uid)
            .whereEqualTo("scheduleId", scheduleId)
            .get(Source.SERVER)
            .addOnSuccessListener fetchA@{ attSnap ->
                if (!isAdded || listeningScheduleId != scheduleId) return@fetchA
                db.collection("archived_attendance")
                    .whereEqualTo("teacherId", uid)
                    .whereEqualTo("scheduleId", scheduleId)
                    .get(Source.SERVER)
                    .addOnSuccessListener fetchB@{ archSnap ->
                        if (!isAdded || listeningScheduleId != scheduleId) return@fetchB
                        lastActiveAttendanceDocs = attSnap.documents
                        lastArchivedAttendanceDocs = archSnap.documents
                        // #region agent log
                        AgentDebugLog.log(
                            "TeacherDashboardFragment:refreshAttendanceMergeFromServer",
                            "server merge",
                            "H_CACHE",
                            mapOf(
                                "scheduleId" to scheduleId,
                                "activeN" to attSnap.documents.size,
                                "archivedN" to archSnap.documents.size
                            )
                        )
                        // #endregion
                        mergeTeacherAttendanceForSession(
                            b.localDayStart, b.localDayEnd, b.extendedDayStart, b.extendedDayEnd,
                            scheduleId, subject
                        )
                    }
                    .addOnFailureListener { e ->
                        if (!isAdded) return@addOnFailureListener
                        android.util.Log.e(TAG, "refresh merge archived get failed", e)
                        AgentDebugLog.appendNdjsonFile(
                            requireContext().applicationContext,
                            "TeacherDashboardFragment:refreshAttendanceMergeFromServer",
                            "archived get failed",
                            "H_FAIL",
                            mapOf("scheduleId" to scheduleId, "err" to (e.message ?: ""))
                        )
                    }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                android.util.Log.e(TAG, "refresh merge attendance get failed", e)
                AgentDebugLog.appendNdjsonFile(
                    requireContext().applicationContext,
                    "TeacherDashboardFragment:refreshAttendanceMergeFromServer",
                    "attendance get failed",
                    "H_FAIL",
                    mapOf("scheduleId" to scheduleId, "err" to (e.message ?: ""))
                )
            }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()
        loadUserData()
        loadRecentAttendance()
    }

    override fun onResume() {
        super.onResume()
        val sid = listeningScheduleId
        val sub = listeningSubject
        if (sid != null && sub != null && (attendanceListener != null || archivedAttendanceListener != null)) {
            startAttendanceListener(sid, sub, forceRefresh = true)
        }
    }

    private fun initializeViews(view: View) {
        imageProfilePic = view.findViewById(R.id.imageProfilePic)
        textInitials = view.findViewById(R.id.textInitials)
        textGreeting = view.findViewById(R.id.textGreeting)
        textName = view.findViewById(R.id.textName)
        textDepartment = view.findViewById(R.id.textDepartment)
        textCurrentClass = view.findViewById(R.id.textCurrentClass)
        badgeLive = view.findViewById(R.id.badgeLive)
        textQrExpiry = view.findViewById(R.id.textQrExpiry)
        textQrSessionMeta = view.findViewById(R.id.textQrSessionMeta)
        textQrLiveIndicator = view.findViewById(R.id.textQrLiveIndicator)
        buttonShowQr = view.findViewById(R.id.buttonShowQr)
        buttonRenewQr = view.findViewById(R.id.buttonRenewQr)
        buttonRefreshAttendance = view.findViewById(R.id.buttonRefreshAttendance)
        buttonManualAdd = view.findViewById(R.id.buttonManualAdd)
        buttonAnalytics = view.findViewById(R.id.buttonAnalytics)
        buttonEndClass = view.findViewById(R.id.buttonEndClass)
        attendanceRecyclerView = view.findViewById(R.id.attendanceRecyclerView)
        textViewAll = view.findViewById(R.id.textViewAll)
        buttonAddManuallyInline = view.findViewById(R.id.buttonAddManuallyInline)
        textPresentCount = view.findViewById(R.id.textPresentCount)
        textAbsentCount = view.findViewById(R.id.textAbsentCount)
        textLateCount = view.findViewById(R.id.textLateCount)

        textGreeting.text = getGreeting()

        attendanceRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        attendanceRecyclerView.setHasFixedSize(false)
        attendanceAdapter = TeacherAttendanceAdapter(attendanceList) { item ->
            confirmAndRemoveAttendance(item)
        }
        attendanceRecyclerView.adapter = attendanceAdapter
        updateAttendanceSummary()
    }

    private fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    private fun setupClickListeners() {
        buttonShowQr.setOnClickListener { showQRCode(forceNew = false) }
        buttonRenewQr.setOnClickListener { showQRCode(forceNew = true) }

        buttonManualAdd.setOnClickListener {
            removeBothAttendanceListeners()
            listeningScheduleId = null
            listeningSubject = null
            loadRecentAttendance()
        }

        buttonRefreshAttendance.setOnClickListener { showManualAddDialog() }
        buttonAddManuallyInline.setOnClickListener { showManualAddDialog() }

        buttonAnalytics.setOnClickListener {
            val intent = Intent(requireContext(), TeacherMainActivity::class.java)
            intent.putExtra("open", "analytics")
            startActivity(intent)
        }

        buttonEndClass.setOnClickListener { confirmEndClass() }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser
        currentUser?.let {
            db.collection("users").document(it.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val name = snapshot.getString("name") ?: "Teacher"
                        val department = snapshot.getString("department") ?: "Department"
                        textName.text = name
                        textDepartment.text = department
                        ProfilePictureManager.getInstance().loadProfilePicture(requireContext(), imageProfilePic, textInitials, name, "TC")
                    }
                }
        }
    }

    private fun showQRCode(forceNew: Boolean = false) {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show(); return
        }

        val currentDay = getCurrentDayOfWeek()
        val nowMinutes = getCurrentTimeInMinutes()

        db.collection("schedules")
            .whereEqualTo("teacherId", currentUser.uid)
            .whereEqualTo("day", currentDay)
            .get()
            .addOnSuccessListener { scheduleDocs ->
                val todays = scheduleDocs.documents
                val match = todays.firstOrNull { doc ->
                    val start = doc.getString("startTime") ?: ""
                    val end = doc.getString("endTime") ?: ""
                    isNowWithinRange(nowMinutes, start, end)
                }

                fun launchFor(doc: com.google.firebase.firestore.DocumentSnapshot) {
                    val intent = android.content.Intent(requireContext(), QRActivity::class.java).apply {
                        putExtra("scheduleId", doc.id)
                        putExtra("subject", doc.getString("subject") ?: "Attendance")
                        putExtra("section", doc.getString("section") ?: "")
                        putExtra("startTime", doc.getString("startTime") ?: "")
                        putExtra("forceNew", forceNew)
                    }
                    startActivity(intent)
                }

                if (match != null) {
                    launchFor(match)
                } else {
                    val next = todays
                        .mapNotNull { doc ->
                            val start = doc.getString("startTime") ?: return@mapNotNull null
                            val startMin = parseTimeToMinutes24(start) ?: return@mapNotNull null
                            Pair(startMin, doc)
                        }
                        .filter { it.first > nowMinutes }
                        .minByOrNull { it.first }
                        ?.second

                    if (next != null) {
                        val subj = next.getString("subject") ?: ""
                        val sec = next.getString("section") ?: ""
                        val start = next.getString("startTime") ?: ""
                        Toast.makeText(requireContext(), "No current class. Next: $subj ($sec) at $start", Toast.LENGTH_LONG).show()
                    } else if (todays.isNotEmpty()) {
                        Toast.makeText(requireContext(), "No more classes today", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "No schedules found for today", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading schedule: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadRecentAttendance(
        afterEndClass: Boolean = false,
        endedScheduleId: String? = null,
        endedSubject: String? = null
    ) {
        val currentUser = auth.currentUser ?: return
        val seq = ++loadRecentAttendanceSeq
        val currentDay = getCurrentDayOfWeek()
        val nowMinutes = getCurrentTimeInMinutes()

        db.collection("schedules")
            .whereEqualTo("teacherId", currentUser.uid)
            .whereEqualTo("day", currentDay)
            .get()
            .addOnSuccessListener { scheduleDocs ->
                if (seq != loadRecentAttendanceSeq) {
                    // #region agent log
                    AgentDebugLog.log(
                        "TeacherDashboardFragment:loadRecentAttendance",
                        "stale schedule callback ignored",
                        "H1",
                        mapOf(
                            "seq" to seq,
                            "currentSeq" to loadRecentAttendanceSeq,
                            "afterEndClass" to afterEndClass
                        )
                    )
                    // #endregion
                    return@addOnSuccessListener
                }
                if (scheduleDocs.isEmpty()) {
                    setNoClassState()
                    resetAttendanceListUntilReload()
                    if (afterEndClass) {
                        removeBothAttendanceListeners()
                        listeningScheduleId = null
                        listeningSubject = null
                        clearRosterSessionContext()
                    } else if (!hasManualEndForToday()) {
                        loadRecentAttendanceFallback()
                    }
                    return@addOnSuccessListener
                }

                val rawCurrentSchedule = scheduleDocs.documents.firstOrNull { doc ->
                    val startTime = doc.getString("startTime") ?: ""
                    val endTime = doc.getString("endTime") ?: ""
                    isNowWithinRange(nowMinutes, startTime, endTime)
                }
                val currentSchedule =
                    if (rawCurrentSchedule != null &&
                        endedSessionKeyForToday(rawCurrentSchedule.id) in sharedDashboard.manuallyEndedSessionKeys
                    ) {
                        null
                    } else {
                        rawCurrentSchedule
                    }

                if (currentSchedule != null && !afterEndClass) {
                    val scheduleId = currentSchedule.id
                    val subject = currentSchedule.getString("subject") ?: ""
                    val section = currentSchedule.getString("section") ?: ""
                    val room = currentSchedule.getString("room") ?: ""
                    val startTime = currentSchedule.getString("startTime") ?: ""
                    val endTime = currentSchedule.getString("endTime") ?: ""

                    setActiveClassState(subject, section, startTime, endTime, scheduleId, room)
                    startAttendanceListener(scheduleId, subject)
                } else {
                    val endedSchedules = scheduleDocs.documents
                        .filter { doc ->
                            val endTime = doc.getString("endTime") ?: return@filter false
                            val endMin = parseTimeToMinutes24(endTime) ?: return@filter false
                            if (endMin < (parseTimeToMinutes24(doc.getString("startTime") ?: "00:00") ?: 0)) {
                                nowMinutes > endMin && nowMinutes < 720
                            } else {
                                nowMinutes > endMin
                            }
                        }

                    if (endedSchedules.isNotEmpty()) {
                        archiveEndedClassesAttendance(endedSchedules)
                    }

                    val nextSchedule = scheduleDocs.documents
                        .mapNotNull { doc ->
                            val startTime = doc.getString("startTime") ?: return@mapNotNull null
                            val startMin = parseTimeToMinutes24(startTime) ?: return@mapNotNull null
                            Pair(startMin, doc)
                        }
                        .filter { it.first > nowMinutes }
                        .minByOrNull { it.first }
                        ?.second

                    if (nextSchedule != null) {
                        val subject = nextSchedule.getString("subject") ?: ""
                        val section = nextSchedule.getString("section") ?: ""
                        val startTime = nextSchedule.getString("startTime") ?: ""
                        setNextClassState(subject, section, startTime)
                    } else {
                        setNoClassState()
                    }

                    removeBothAttendanceListeners()
                    listeningScheduleId = null
                    listeningSubject = null
                    buttonEndClass.visibility = View.GONE
                    resetAttendanceListUntilReload()
                    val skipFallbackForManualEnd =
                        !afterEndClass &&
                            (
                                (rawCurrentSchedule != null &&
                                    endedSessionKeyForToday(rawCurrentSchedule.id) in sharedDashboard.manuallyEndedSessionKeys) ||
                                    hasManualEndForToday()
                            )
                    when {
                        afterEndClass -> {
                            // #region agent log
                            AgentDebugLog.log(
                                "TeacherDashboardFragment:loadRecentAttendance",
                                "after end class: no fallback",
                                "H5",
                                mapOf(
                                    "endedScheduleId" to (endedScheduleId ?: ""),
                                    "endedSubject" to (endedSubject ?: ""),
                                    "listSize" to attendanceList.size,
                                    "adapterItemCount" to (attendanceAdapter?.itemCount ?: -1),
                                    "rvSameAdapter" to (attendanceRecyclerView.adapter === attendanceAdapter),
                                    "suppress" to sharedDashboard.suppressDashboardAttendanceFromFirestore,
                                    "runId" to "verify1"
                                )
                            )
                            // #endregion
                        }
                        skipFallbackForManualEnd -> {
                            // #region agent log
                            AgentDebugLog.log(
                                "TeacherDashboardFragment:loadRecentAttendance",
                                "skip fallback manual end same window",
                                "H_END",
                                mapOf("scheduleId" to (rawCurrentSchedule?.id ?: ""))
                            )
                            // #endregion
                        }
                        else -> loadRecentAttendanceFallback()
                    }
                }
            }
            .addOnFailureListener { e ->
                if (seq != loadRecentAttendanceSeq) return@addOnFailureListener
                Toast.makeText(requireContext(), "Error loading schedule: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private var activeScheduleId: String? = null

    private fun setActiveClassState(subject: String, section: String, startTime: String, endTime: String, scheduleId: String, room: String = "") {
        val classLabel = if (room.isNotBlank()) "$subject — $section — $room" else "$subject — $section"
        textCurrentClass.text = classLabel
        badgeLive.visibility = View.VISIBLE
        activeScheduleId = scheduleId
        currentScheduleSection = section
        currentScheduleStartTime = startTime
        currentScheduleEndTime = endTime

        val metaLabel = if (room.isNotBlank()) "$subject · $section · $room" else "$subject · $section"
        textQrSessionMeta.text = metaLabel
        textQrExpiry.text = "Today, $startTime – $endTime"
        textQrLiveIndicator.visibility = View.VISIBLE
        textQrLiveIndicator.text = "● In session"

        loadQrSessionExpiry()
    }

    private fun loadQrSessionExpiry() {
        val uid = auth.currentUser?.uid ?: return
        val sid = activeScheduleId ?: return
        val now = System.currentTimeMillis()

        db.collection("attendance_sessions")
            .whereEqualTo("teacherId", uid)
            .whereEqualTo("scheduleId", sid)
            .get()
            .addOnSuccessListener { snapshot ->
                val active = snapshot.documents
                    .filter { (it.getLong("expiresAt") ?: 0L) > now }
                    .maxByOrNull { it.getLong("expiresAt") ?: 0L }

                if (active != null) {
                    val expiresAt = active.getLong("expiresAt") ?: return@addOnSuccessListener
                    val remainingMin = ((expiresAt - now) / 60000).toInt()
                    if (remainingMin > 0) {
                        textQrLiveIndicator.text = "● Expires in $remainingMin min"
                    } else {
                        textQrLiveIndicator.text = "● QR expired"
                    }
                } else {
                    textQrLiveIndicator.text = "● No active QR"
                }
            }
    }

    private fun setNextClassState(subject: String, section: String, startTime: String) {
        textCurrentClass.text = "Next: $subject ($section) at $startTime"
        badgeLive.visibility = View.GONE

        textQrSessionMeta.text = "Upcoming"
        textQrExpiry.text = "$subject at $startTime"
        textQrLiveIndicator.visibility = View.GONE
    }

    private fun setNoClassState() {
        textCurrentClass.text = "No classes scheduled for today"
        badgeLive.visibility = View.GONE

        textQrSessionMeta.text = "No active session"
        textQrExpiry.text = "No classes scheduled"
        textQrLiveIndicator.visibility = View.GONE
    }

    private fun isTodaysAttendanceRow(
        doc: DocumentSnapshot,
        localDayStart: com.google.firebase.Timestamp,
        localDayEnd: com.google.firebase.Timestamp,
        extendedDayStart: com.google.firebase.Timestamp,
        extendedDayEnd: com.google.firebase.Timestamp
    ): Boolean {
        val ts = doc.getTimestamp("timestamp")
        val sessionId = doc.getString("sessionId") ?: ""
        if (ts == null) return true
        if (ts >= localDayStart && ts < localDayEnd) return true
        if (sessionId.isNotEmpty() && !sessionId.startsWith("MANUAL_") &&
            ts >= extendedDayStart && ts < extendedDayEnd) return true
        return false
    }

    private fun removeBothAttendanceListeners() {
        attendanceListener?.remove()
        attendanceListener = null
        archivedAttendanceListener?.remove()
        archivedAttendanceListener = null
        lastActiveAttendanceDocs = emptyList()
        lastArchivedAttendanceDocs = emptyList()
        stopRosterAbsentTicker()
    }

    private fun stopRosterAbsentTicker() {
        rosterAbsentTicker?.let { classEndHandler.removeCallbacks(it) }
        rosterAbsentTicker = null
    }

    private fun scheduleRosterAbsentTicker() {
        stopRosterAbsentTicker()
        rosterAbsentTicker = Runnable {
            if (!isAdded || listeningScheduleId == null) return@Runnable
            updateAttendanceSummary()
            classEndHandler.postDelayed(rosterAbsentTicker!!, 60_000L)
        }
        classEndHandler.postDelayed(rosterAbsentTicker!!, 60_000L)
    }

    private fun clearRosterSessionContext() {
        rosterStudentIds = emptySet()
        currentScheduleSection = null
        currentScheduleStartTime = null
        currentScheduleEndTime = null
        stopRosterAbsentTicker()
    }

    private fun millisUntilClassEnd(): Long? {
        val end = currentScheduleEndTime ?: return null
        val start = currentScheduleStartTime ?: return null
        val nowMin = getCurrentTimeInMinutes()
        if (!isNowWithinRange(nowMin, start, end)) return null
        val eM = parseTimeToMinutes24(end) ?: return null
        val sM = parseTimeToMinutes24(start) ?: return null
        val (eh, em) = parseTime24(end)
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.set(java.util.Calendar.HOUR_OF_DAY, eh)
        cal.set(java.util.Calendar.MINUTE, em)
        if (eM < sM) {
            if (nowMin >= sM) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val diff = cal.timeInMillis - System.currentTimeMillis()
        return if (diff > 0) diff else null
    }

    private fun scannedStudentIdsForCurrentScheduleFromSnapshots(): Set<String> {
        val sid = listeningScheduleId ?: return emptySet()
        val b = computeSessionDayBounds()
        val combined = (lastActiveAttendanceDocs + lastArchivedAttendanceDocs).distinctBy { it.id }
        val out = mutableSetOf<String>()
        for (doc in combined) {
            val docSid = normalizeScheduleIdFromSnapshot(doc) ?: doc.getString("scheduleId") ?: continue
            if (docSid != sid) continue
            if (!isTodaysAttendanceRow(doc, b.localDayStart, b.localDayEnd, b.extendedDayStart, b.extendedDayEnd)) continue
            val uid = doc.getString("studentId") ?: doc.getString("userId") ?: continue
            if (uid.isNotBlank()) out.add(uid)
        }
        return out
    }

    private fun fetchRosterStudentIdsForSchedule() {
        val target = currentScheduleSection?.trim()?.lowercase() ?: return
        if (target.isEmpty()) return
        db.collection("users")
            .whereEqualTo("role", "student")
            .get()
            .addOnSuccessListener { studentDocs ->
                if (!isAdded) return@addOnSuccessListener
                rosterStudentIds = studentDocs.documents
                    .filter { (it.getString("role") ?: "") == "student" }
                    .filter { (it.getString("section") ?: "").trim().lowercase() == target }
                    .map { it.id }
                    .toSet()
                updateAttendanceSummary()
            }
            .addOnFailureListener { }
    }

    private fun prunePendingHiddenFromCombined(combined: List<DocumentSnapshot>) {
        pendingHiddenAttendanceIds.removeAll { hid ->
            combined.isNotEmpty() &&
                combined.any { it.id == hid && docExcludedFromTeacherDashboard(it) }
        }
    }

    private fun mergeTeacherAttendanceForSession(
        localDayStart: com.google.firebase.Timestamp,
        localDayEnd: com.google.firebase.Timestamp,
        extendedDayStart: com.google.firebase.Timestamp,
        extendedDayEnd: com.google.firebase.Timestamp,
        scheduleId: String,
        subject: String
    ) {
        if (!isAdded) return
        if (listeningScheduleId != scheduleId) return
        if (sharedDashboard.suppressDashboardAttendanceFromFirestore) {
            // #region agent log
            AgentDebugLog.log(
                "TeacherDashboardFragment:mergeTeacherAttendanceForSession",
                "suppress path: clear only",
                "H3",
                mapOf(
                    "listSizeBeforeClear" to attendanceList.size,
                    "scheduleId" to scheduleId,
                    "runId" to "verify1"
                )
            )
            // #endregion
            attendanceList.clear()
            attendanceAdapter?.notifyDataSetChanged()
            updateAttendanceSummary()
            return
        }
        // #region agent log
        AgentDebugLog.log(
            "TeacherDashboardFragment:mergeTeacherAttendanceForSession",
            "entry",
            "H3",
            mapOf(
                "runId" to "verify1",
                "scheduleId" to scheduleId,
                "listeningScheduleId" to (listeningScheduleId ?: ""),
                "suppress" to sharedDashboard.suppressDashboardAttendanceFromFirestore,
                "listSizeBefore" to attendanceList.size,
                "lastActiveN" to lastActiveAttendanceDocs.size,
                "lastArchivedN" to lastArchivedAttendanceDocs.size
            )
        )
        // #endregion
        val combined = (lastActiveAttendanceDocs + lastArchivedAttendanceDocs).distinctBy { it.id }
        prunePendingHiddenFromCombined(combined)
        val filtered = combined.filter { doc ->
            if (doc.id in pendingHiddenAttendanceIds) return@filter false
            if (isScheduleManuallyEndedTodayForDoc(doc)) return@filter false
            if (docExcludedFromTeacherDashboard(doc)) return@filter false
            isTodaysAttendanceRow(doc, localDayStart, localDayEnd, extendedDayStart, extendedDayEnd)
        }
        val raw = lastActiveAttendanceDocs.size
        val archivedN = lastArchivedAttendanceDocs.size
        android.util.Log.w(TAG, "attendance merged active=$raw archived=$archivedN filtered=${filtered.size} scheduleId=$scheduleId")
        // #region agent log
        AgentDebugLog.log(
            "TeacherDashboardFragment:mergeTeacherAttendanceForSession",
            "merged",
            "H7",
            mapOf(
                "rawActive" to raw,
                "rawArchived" to archivedN,
                "filtered" to filtered.size,
                "scheduleId" to scheduleId,
                "subject" to subject
            )
        )
        // #endregion
        // #region agent log
        AgentDebugLog.log(
            "TeacherDashboardFragment:mergeTeacherAttendanceForSession",
            "pending hidden applied",
            "H_PENDING",
            mapOf(
                "pendingN" to pendingHiddenAttendanceIds.size,
                "filtered" to filtered.size
            )
        )
        // #endregion
        val activeIds = lastActiveAttendanceDocs.map { it.id }.toSet()
        val archivedIds = lastArchivedAttendanceDocs.map { it.id }.toSet()
        val newAttendanceList = filtered
            .sortedByDescending { doc -> doc.getTimestamp("timestamp")?.seconds ?: 0L }
            .map { doc ->
                val inActive = doc.id in activeIds
                val inArchived = doc.id in archivedIds
                val source = when {
                    inActive && inArchived -> AttendanceDocSource.BOTH
                    inActive -> AttendanceDocSource.ACTIVE
                    else -> AttendanceDocSource.ARCHIVED
                }
                TeacherAttendanceItem(
                    documentId = doc.id,
                    studentName = doc.getString("studentName") ?: "Unknown Student",
                    timeTaken = formatTimestamp(doc.getTimestamp("timestamp")),
                    section = doc.getString("section") ?: "",
                    status = (doc.getString("status") ?: "PRESENT").trim().uppercase(),
                    source = source
                )
            }
        val oldSize = attendanceList.size
        attendanceList.clear()
        attendanceList.addAll(newAttendanceList)
        attendanceAdapter?.notifyDataSetChanged()
        updateAttendanceSummary()
        attendanceRecyclerView.post {
            // #region agent log
            AgentDebugLog.log(
                "TeacherDashboardFragment:attendanceRecyclerView.post",
                "after notify",
                "H3",
                mapOf(
                    "rvHeight" to attendanceRecyclerView.height,
                    "rvMeasuredH" to attendanceRecyclerView.measuredHeight,
                    "adapterCount" to (attendanceAdapter?.itemCount ?: -1),
                    "listSize" to attendanceList.size
                )
            )
            // #endregion
            updateAttendanceSummary()
            attendanceRecyclerView.requestLayout()
            (attendanceRecyclerView.parent as? View)?.requestLayout()
        }
        if (newAttendanceList.size > oldSize) {
            attendanceRecyclerView.smoothScrollToPosition(0)
        }
    }

    private fun startAttendanceListener(scheduleId: String, subject: String, forceRefresh: Boolean = false) {
        val currentUser = auth.currentUser ?: return
        if (!forceRefresh && listeningScheduleId == scheduleId && listeningSubject == subject && attendanceListener != null && archivedAttendanceListener != null) {
            return
        }
        if (endedSessionKeyForToday(scheduleId) in sharedDashboard.manuallyEndedSessionKeys) {
            removeBothAttendanceListeners()
            listeningScheduleId = null
            listeningSubject = null
            return
        }
        removeBothAttendanceListeners()
        listeningScheduleId = scheduleId
        listeningSubject = subject

        db.collection("schedules").document(scheduleId)
            .get()
            .addOnSuccessListener { scheduleDoc ->
                // #region agent log
                AgentDebugLog.log(
                    "TeacherDashboardFragment:startAttendanceListener",
                    "schedule get success",
                    "H1",
                    mapOf(
                        "runId" to "pre",
                        "expectedScheduleId" to scheduleId,
                        "listeningScheduleId" to (listeningScheduleId ?: ""),
                        "match" to (listeningScheduleId == scheduleId)
                    )
                )
                // #endregion
                if (!scheduleDoc.exists()) {
                    if (isAdded) {
                        listeningScheduleId = null
                        listeningSubject = null
                        activeScheduleId = null
                        clearRosterSessionContext()
                        removeBothAttendanceListeners()
                        buttonEndClass.visibility = View.GONE
                        setNoClassState()
                        Toast.makeText(requireContext(), "Schedule not found", Toast.LENGTH_SHORT).show()
                        if (!hasManualEndForToday()) loadRecentAttendanceFallback()
                    }
                    return@addOnSuccessListener
                }
                if (listeningScheduleId != scheduleId) return@addOnSuccessListener
                val endTime = scheduleDoc.getString("endTime") ?: "23:59"
                val startTime = scheduleDoc.getString("startTime") ?: "00:00"
                currentScheduleSection = scheduleDoc.getString("section") ?: currentScheduleSection
                currentScheduleStartTime = startTime
                currentScheduleEndTime = endTime

                buttonEndClass.visibility = View.VISIBLE
                scheduleAutoArchiveAtClassEnd(endTime)

                removeBothAttendanceListeners()

                fun fetchBothCollectionsAndMerge() {
                    refreshAttendanceMergeFromServer(scheduleId, subject)
                }

                attendanceListener = db.collection("attendance")
                    .whereEqualTo("teacherId", currentUser.uid)
                    .whereEqualTo("scheduleId", scheduleId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            val msg = e.message ?: "Unknown error"
                            android.util.Log.e(TAG, "Attendance listen failed: $msg", e)
                            if (isAdded && !isDetached) {
                                val hint = if (msg.contains("index", ignoreCase = true)) " Deploy indexes: firebase deploy --only firestore:indexes" else ""
                                Toast.makeText(requireContext(), "Attendance listen failed: $msg$hint", Toast.LENGTH_LONG).show()
                            }
                            return@addSnapshotListener
                        }
                        if (!isAdded || snapshot == null) return@addSnapshotListener
                        fetchBothCollectionsAndMerge()
                    }

                archivedAttendanceListener = db.collection("archived_attendance")
                    .whereEqualTo("teacherId", currentUser.uid)
                    .whereEqualTo("scheduleId", scheduleId)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            val msg = e.message ?: "Unknown error"
                            android.util.Log.e(TAG, "Archived attendance listen failed: $msg", e)
                            if (isAdded && !isDetached) {
                                val hint = if (msg.contains("index", ignoreCase = true)) " Deploy indexes: firebase deploy --only firestore:indexes" else ""
                                Toast.makeText(requireContext(), "Archived listen failed: $msg$hint", Toast.LENGTH_LONG).show()
                            }
                            return@addSnapshotListener
                        }
                        if (!isAdded || snapshot == null) return@addSnapshotListener
                        fetchBothCollectionsAndMerge()
                    }

                sharedDashboard.suppressDashboardAttendanceFromFirestore = false

                fetchRosterStudentIdsForSchedule()
                scheduleRosterAbsentTicker()
            }
            .addOnFailureListener { e ->
                android.util.Log.e(TAG, "Error getting schedule: ${e.message}", e)
                if (!isAdded) return@addOnFailureListener
                listeningScheduleId = null
                listeningSubject = null
                activeScheduleId = null
                clearRosterSessionContext()
                removeBothAttendanceListeners()
                buttonEndClass.visibility = View.GONE
                setNoClassState()
                Toast.makeText(
                    requireContext(),
                    "Schedule load failed: ${e.message ?: "unknown"}",
                    Toast.LENGTH_LONG
                ).show()
                if (!hasManualEndForToday()) loadRecentAttendanceFallback()
            }
    }

    private fun parseTime24(time: String): Pair<Int, Int> {
        return try {
            val parts = time.split(":")
            Pair(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    private fun loadRecentAttendanceFallback() {
        val uid = auth.currentUser?.uid ?: return

        removeBothAttendanceListeners()
        listeningScheduleId = null
        listeningSubject = null
        clearRosterSessionContext()

        if (hasManualEndForToday()) {
            attendanceList.clear()
            attendanceAdapter?.notifyDataSetChanged()
            updateAttendanceSummary()
            return
        }

        attendanceListener = db.collection("attendance")
            .whereEqualTo("teacherId", uid)
            .addSnapshotListener { activeSnapshot, e ->
                if (e != null) {
                    val msg = e.message ?: "Unknown error"
                    android.util.Log.e(TAG, "Attendance fallback listen failed: $msg", e)
                    if (isAdded && !isDetached) {
                        val hint = if (msg.contains("index", ignoreCase = true)) " Deploy: firebase deploy --only firestore:indexes" else ""
                        Toast.makeText(requireContext(), "Listen failed: $msg$hint", Toast.LENGTH_LONG).show()
                    }
                    return@addSnapshotListener
                }

                if (sharedDashboard.suppressDashboardAttendanceFromFirestore) {
                    attendanceList.clear()
                    attendanceAdapter?.notifyDataSetChanged()
                    updateAttendanceSummary()
                    return@addSnapshotListener
                }

                val activeDocs = activeSnapshot?.documents ?: emptyList()
                activeDocs.forEach { doc ->
                    if (docExcludedFromTeacherDashboard(doc)) pendingHiddenAttendanceIds.remove(doc.id)
                }
                val sortedDocs = activeDocs
                    .filter { it.id !in pendingHiddenAttendanceIds }
                    .filter { !isScheduleManuallyEndedTodayForDoc(it) }
                    .filter { !docExcludedFromTeacherDashboard(it) }
                    .sortedByDescending { it.getTimestamp("timestamp")?.seconds ?: 0 }
                    .take(RECENT_ATTENDANCE_LIMIT)

                val newAttendanceList = sortedDocs.map { doc ->
                    TeacherAttendanceItem(
                        documentId = doc.id,
                        studentName = doc.getString("studentName") ?: "Unknown Student",
                        timeTaken = formatTimestamp(doc.getTimestamp("timestamp")),
                        section = doc.getString("section") ?: "",
                        status = (doc.getString("status") ?: "PRESENT").trim().uppercase(),
                        source = AttendanceDocSource.ACTIVE
                    )
                }
                // #region agent log
                AgentDebugLog.log(
                    "TeacherDashboardFragment:loadRecentAttendanceFallback",
                    "fallback snapshot",
                    "H2",
                    mapOf(
                        "sortedDocs" to sortedDocs.size,
                        "newListSize" to newAttendanceList.size,
                        "pendingN" to pendingHiddenAttendanceIds.size,
                        "activeDocsN" to activeDocs.size,
                        "suppress" to sharedDashboard.suppressDashboardAttendanceFromFirestore,
                        "hasManualEnd" to hasManualEndForToday(),
                        "runId" to "verify1"
                    )
                )
                // #endregion

                attendanceList.clear()
                attendanceList.addAll(newAttendanceList)

                attendanceAdapter = TeacherAttendanceAdapter(attendanceList) { item ->
                    confirmAndRemoveAttendance(item)
                }
                attendanceRecyclerView.adapter = attendanceAdapter
                updateAttendanceSummary()
            }
    }

    private fun resetAttendanceListUntilReload() {
        clearRosterSessionContext()
        attendanceList.clear()
        attendanceAdapter?.notifyDataSetChanged()
        // #region agent log
        AgentDebugLog.log(
            "TeacherDashboardFragment:resetAttendanceListUntilReload",
            "cleared pending reload",
            "H_CNT",
            mapOf("listeningScheduleId" to (listeningScheduleId ?: ""))
        )
        // #endregion
        updateAttendanceSummary()
    }

    private fun updateAttendanceSummary() {
        fun norm(s: String) = s.trim().uppercase()
        val presentCount = attendanceList.count { norm(it.status) == "PRESENT" }
        val lateCount = attendanceList.count { norm(it.status) == "LATE" }
        var absentCount = attendanceList.count {
            val n = norm(it.status)
            n == "ABSENT" || n == "CUTTING" || n == "EXCUSED"
        }
        val msLeft = millisUntilClassEnd()
        val rosterMode = false
        var scannedN = 0
        // #region agent log
        AgentDebugLog.log(
            "TeacherDashboardFragment:updateAttendanceSummary",
            "counters",
            "H5",
            mapOf(
                "present" to presentCount,
                "late" to lateCount,
                "absent" to absentCount,
                "listSize" to attendanceList.size,
                "listeningScheduleId" to (listeningScheduleId ?: ""),
                "rosterMode" to rosterMode,
                "msLeft" to (msLeft ?: -1L),
                "rosterN" to rosterStudentIds.size,
                "scannedN" to scannedN
            )
        )
        // #endregion
        textViewAll.text = "View all ${attendanceList.size} students ›"
        textPresentCount.text = presentCount.toString()
        textAbsentCount.text = absentCount.toString()
        textLateCount.text = lateCount.toString()
    }

    private fun confirmAndRemoveAttendance(item: TeacherAttendanceItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove attendance")
            .setMessage("Remove ${item.studentName}'s attendance?")
            .setPositiveButton("Remove") { _, _ ->
                val attendanceId = item.documentId
                if (attendanceId.isBlank()) {
                    Toast.makeText(requireContext(), "Invalid attendance row", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (auth.currentUser?.uid == null) {
                    Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val db = FirebaseFirestore.getInstance()
                val attRef = db.collection("attendance").document(attendanceId)
                val archRef = db.collection("archived_attendance").document(attendanceId)
                val scheduleIdFallback = listeningScheduleId
                attRef.get(Source.SERVER)
                    .addOnSuccessListener attGet@{ attSnap ->
                        if (!isAdded) return@attGet
                        if (attSnap.exists()) {
                            val merge = mergePayloadHideFromDashboard(attSnap, scheduleIdFallback)
                            if (merge == null) {
                                Toast.makeText(
                                    requireContext(),
                                    "Cannot remove: row has no schedule id. Re-open the dashboard from an active class.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@attGet
                            }
                            attRef.set(merge, SetOptions.merge())
                                .addOnSuccessListener {
                                    onHideAttendanceFromDashboardSuccess(attendanceId, item.studentName, "merge_active")
                                }
                                .addOnFailureListener { e ->
                                    onHideAttendanceFromDashboardFailure(attendanceId, e)
                                }
                        } else {
                            archRef.get(Source.SERVER)
                                .addOnSuccessListener archGet@{ archSnap ->
                                    if (!isAdded) return@archGet
                                    if (archSnap.exists()) {
                                        val merge = mergePayloadHideFromDashboard(archSnap, scheduleIdFallback)
                                        if (merge == null) {
                                            Toast.makeText(
                                                requireContext(),
                                                "Cannot remove: row has no schedule id.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@archGet
                                        }
                                        archRef.set(merge, SetOptions.merge())
                                            .addOnSuccessListener {
                                                onHideAttendanceFromDashboardSuccess(attendanceId, item.studentName, "merge_archived")
                                            }
                                            .addOnFailureListener { e ->
                                                onHideAttendanceFromDashboardFailure(attendanceId, e)
                                            }
                                    } else {
                                        onHideAttendanceFromDashboardFailure(
                                            attendanceId,
                                            FirebaseFirestoreException("not found", FirebaseFirestoreException.Code.NOT_FOUND)
                                        )
                                    }
                                }
                                .addOnFailureListener { e ->
                                    onHideAttendanceFromDashboardFailure(attendanceId, e)
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        onHideAttendanceFromDashboardFailure(attendanceId, e)
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onHideAttendanceFromDashboardSuccess(attendanceId: String, studentName: String, path: String) {
        if (!isAdded) return
        pendingHiddenAttendanceIds.add(attendanceId)
        // #region agent log
        AgentDebugLog.log(
            "TeacherDashboardFragment:onHideAttendanceFromDashboardSuccess",
            "exclude ok",
            "H_SOFT",
                mapOf(
                "attendanceId" to attendanceId,
                "path" to path,
                "listeningScheduleId" to (listeningScheduleId ?: "")
            )
        )
        // #endregion
        AgentDebugLog.appendNdjsonFile(
            requireContext().applicationContext,
            "TeacherDashboardFragment:onHideAttendanceFromDashboardSuccess",
            "exclude ok",
            "H_SOFT",
            mapOf("attendanceId" to attendanceId, "path" to path)
        )
        val sidLive = listeningScheduleId
        val subLive = listeningSubject
        if (sidLive != null && subLive != null) {
            attendanceList.removeAll { it.documentId == attendanceId }
            attendanceAdapter?.notifyDataSetChanged()
            updateAttendanceSummary()
            refreshAttendanceMergeFromServer(sidLive, subLive)
        } else {
            attendanceList.removeAll { it.documentId == attendanceId }
            attendanceAdapter?.notifyDataSetChanged()
            updateAttendanceSummary()
        }
        Toast.makeText(requireContext(), "Removed $studentName", Toast.LENGTH_SHORT).show()
    }

    private fun onHideAttendanceFromDashboardFailure(attendanceId: String, e: Exception) {
        if (!isAdded) return
        val code = (e as? FirebaseFirestoreException)?.code?.name ?: ""
        android.util.Log.e(TAG, "hide attendance failed id=$attendanceId code=$code", e)
        AgentDebugLog.appendNdjsonFile(
            requireContext().applicationContext,
            "TeacherDashboardFragment:onHideAttendanceFromDashboardFailure",
            "exclude failed",
            "H_SOFT",
            mapOf(
                "attendanceId" to attendanceId,
                "code" to code,
                "error" to (e.message ?: "")
            )
        )
        Toast.makeText(
            requireContext(),
            "Failed: ${e.message ?: "Unknown error"} ($code)",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun getCurrentDayOfWeek(): String {
        val calendar = java.util.Calendar.getInstance()
        val dayFormat = java.text.SimpleDateFormat("EEEE", java.util.Locale.ENGLISH)
        return dayFormat.format(calendar.time)
    }

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        return timestamp?.toDate()?.let { date ->
            val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            format.format(date)
        } ?: ""
    }

    private fun showManualAddDialog() {
        val currentUser = auth.currentUser ?: return
        val nowMinutes = getCurrentTimeInMinutes()
        val currentDay = getCurrentDayOfWeek()

        db.collection("schedules")
            .whereEqualTo("teacherId", currentUser.uid)
            .whereEqualTo("day", currentDay)
            .get()
            .addOnSuccessListener { scheduleDocs ->
                val todays = scheduleDocs.documents
                val currentSchedule = todays.firstOrNull { doc ->
                    val start = doc.getString("startTime") ?: ""
                    val end = doc.getString("endTime") ?: ""
                    isNowWithinRange(nowMinutes, start, end)
                }

                fun proceedWith(doc: com.google.firebase.firestore.DocumentSnapshot) {
                    val subject = doc.getString("subject") ?: ""
                    val section = doc.getString("section") ?: ""
                    loadStudentsForSection(section, subject, doc.id)
                }

                if (currentSchedule != null) {
                    proceedWith(currentSchedule)
                } else if (todays.isNotEmpty()) {
                    showSchedulePicker(todays, title = "Select class for manual add") { chosen ->
                        proceedWith(chosen)
                    }
                } else {
                    Toast.makeText(requireContext(), "No schedules found for today", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading schedule: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getCurrentTimeInMinutes(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    private fun parseTimeToMinutes24(time: String): Int? {
        return try {
            val parts = time.split(":")
            val hour = parts.getOrNull(0)?.toInt() ?: return null
            val minute = parts.getOrNull(1)?.toInt() ?: 0
            hour * 60 + minute
        } catch (_: Exception) { null }
    }

    private fun isNowWithinRange(nowMinutes: Int, start: String, end: String): Boolean {
        val s = parseTimeToMinutes24(start) ?: return false
        val e = parseTimeToMinutes24(end) ?: return false
        return if (e < s) {
            nowMinutes >= s || nowMinutes <= e
        } else {
            nowMinutes in s until e
        }
    }

    private fun showSchedulePicker(
        schedules: List<com.google.firebase.firestore.DocumentSnapshot>,
        title: String,
        onChosen: (com.google.firebase.firestore.DocumentSnapshot) -> Unit
    ) {
        val items = schedules.map { doc ->
            val subject = doc.getString("subject") ?: ""
            val section = doc.getString("section") ?: ""
            val start = doc.getString("startTime") ?: ""
            val end = doc.getString("endTime") ?: ""
            "$subject ($section)  $start-$end"
        }.toTypedArray()

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(items) { dialog, which ->
                onChosen(schedules[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadStudentsForSection(section: String, subject: String, scheduleId: String) {
        val target = section.trim().lowercase()
        db.collection("users")
            .whereEqualTo("role", "student")
            .get()
            .addOnSuccessListener { studentDocs ->
                val students = studentDocs
                    .filter { (it.getString("role") ?: "") == "student" }
                    .mapNotNull { doc ->
                        val studentSection = (doc.getString("section") ?: "").trim().lowercase()
                        if (studentSection == target) {
                            StudentItem(
                                id = doc.id,
                                name = doc.getString("name") ?: "Unknown Student",
                                email = doc.getString("email") ?: "",
                                section = doc.getString("section") ?: ""
                            )
                        } else null
                    }

                if (students.isNotEmpty()) {
                    showStudentSelectionDialog(students, subject, scheduleId)
                } else {
                    Toast.makeText(requireContext(), "No students found in section $section", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showStudentSelectionDialog(students: List<StudentItem>, subject: String, scheduleId: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_manual_add_student, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val textCurrentSession = dialogView.findViewById<TextView>(R.id.textCurrentSession)
        val radioPresent = dialogView.findViewById<RadioButton>(R.id.radioPresent)
        val radioExcused = dialogView.findViewById<RadioButton>(R.id.radioExcused)
        val radioCutting = dialogView.findViewById<RadioButton>(R.id.radioCutting)

        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnAddStudent = dialogView.findViewById<Button>(R.id.btnAddStudent)

        textCurrentSession.text = "Current Session: $subject"

        val autoComplete = dialogView.findViewById<AutoCompleteTextView>(R.id.editStudentName)
        val studentNames = students.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, studentNames)
        autoComplete.setAdapter(adapter)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAddStudent.setOnClickListener {
            val studentName = dialogView.findViewById<AutoCompleteTextView>(R.id.editStudentName).text.toString().trim()

            if (studentName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a student name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val status = when {
                radioExcused.isChecked -> "EXCUSED"
                radioCutting.isChecked -> "ABSENT"
                else -> "PRESENT"
            }

            val selectedStudent = students.find { it.name.equals(studentName, ignoreCase = true) }
            if (selectedStudent != null) {
                addStudentsManually(listOf(selectedStudent), subject, scheduleId, status)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Student '$studentName' not found in current section", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun addStudentsManually(students: List<StudentItem>, subject: String, scheduleId: String, status: String) {
        val currentUser = auth.currentUser ?: return
        val currentTime = com.google.firebase.Timestamp.now()

        students.forEach { student ->
            val attendanceData = hashMapOf(
                "studentId" to student.id,
                "userId" to student.id,
                "studentName" to student.name,
                "sessionId" to "MANUAL_${System.currentTimeMillis()}",
                "teacherId" to currentUser.uid,
                "scheduleId" to scheduleId,
                "subject" to subject,
                "section" to student.section,
                "timestamp" to currentTime,
                "status" to status,
                "location" to "",
                "notes" to "Manually added by teacher - Status: $status",
                "isManualEntry" to true
            )

            db.collection("attendance").add(attendanceData)
                .addOnSuccessListener { docRef ->
                    // #region agent log
                    AgentDebugLog.log(
                        "TeacherDashboardFragment:addStudentsManually",
                        "write ok",
                        "H6",
                        mapOf(
                            "newDocId" to docRef.id,
                            "scheduleId" to scheduleId,
                            "listeningScheduleId" to (listeningScheduleId ?: "")
                        )
                    )
                    // #endregion
                    Toast.makeText(requireContext(), "Added ${student.name} to attendance", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Failed to add ${student.name}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        startAttendanceListener(scheduleId, subject)
    }

    private fun confirmEndClass() {
        AlertDialog.Builder(requireContext())
            .setTitle("End Class")
            .setMessage("This will archive all attendance from this class session. Continue?")
            .setPositiveButton("End Class") { _, _ -> archiveCurrentClassAttendance() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun archiveCurrentClassAttendance() {
        val scheduleId = listeningScheduleId ?: return
        val subject = listeningSubject ?: return
        val currentUser = auth.currentUser ?: return
        sharedDashboard.suppressDashboardAttendanceFromFirestore = true
        loadRecentAttendanceSeq++

        val uiDocIds = attendanceList.map { it.documentId }.filter { it.isNotBlank() }
        uiDocIds.forEach { pendingHiddenAttendanceIds.add(it) }
        sharedDashboard.manuallyEndedSessionKeys.add(endedSessionKeyForToday(scheduleId))

        classEndRunnable?.let { classEndHandler.removeCallbacks(it) }
        classEndRunnable = null
        buttonEndClass.visibility = View.GONE
        removeBothAttendanceListeners()
        listeningScheduleId = null
        listeningSubject = null
        attendanceList.clear()
        attendanceAdapter?.notifyDataSetChanged()
        updateAttendanceSummary()
        // #region agent log
        AgentDebugLog.log(
            "TeacherDashboardFragment:archiveCurrentClassAttendance",
            "sync after clear notify",
            "H4",
            mapOf(
                "listSize" to attendanceList.size,
                "adapterCount" to (attendanceAdapter?.itemCount ?: -1),
                "rvSameAdapter" to (attendanceRecyclerView.adapter === attendanceAdapter),
                "suppress" to sharedDashboard.suppressDashboardAttendanceFromFirestore,
                "thread" to Thread.currentThread().name,
                "runId" to "verify1"
            )
        )
        // #endregion

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dayStartCal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val localDayStart = com.google.firebase.Timestamp(dayStartCal.time)
                val dayEndCal = (dayStartCal.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
                val localDayEnd = com.google.firebase.Timestamp(dayEndCal.time)
                val extStartCal = (dayStartCal.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.HOUR_OF_DAY, -2)
                }
                val extEndCal = (dayEndCal.clone() as java.util.Calendar).apply {
                    add(java.util.Calendar.HOUR_OF_DAY, 2)
                }
                val extendedDayStart = com.google.firebase.Timestamp(extStartCal.time)
                val extendedDayEnd = com.google.firebase.Timestamp(extEndCal.time)

                var docs = db.collection("attendance")
                    .whereEqualTo("teacherId", currentUser.uid)
                    .whereEqualTo("scheduleId", scheduleId)
                    .get()
                    .await()
                    .documents
                if (docs.isEmpty()) {
                    val asLong = scheduleId.toLongOrNull()
                    if (asLong != null) {
                        docs = db.collection("attendance")
                            .whereEqualTo("teacherId", currentUser.uid)
                            .whereEqualTo("scheduleId", asLong)
                            .get()
                            .await()
                            .documents
                    }
                }
                if (docs.isEmpty() && uiDocIds.isNotEmpty()) {
                    docs = uiDocIds.mapNotNull { id ->
                        db.collection("attendance").document(id).get().await().takeIf { it.exists() }
                    }.filter { doc ->
                        val tid = doc.getString("teacherId")
                        tid == currentUser.uid || tid.isNullOrBlank()
                    }
                }

                var toArchive = docs.filter { doc ->
                    isTodaysAttendanceRow(doc, localDayStart, localDayEnd, extendedDayStart, extendedDayEnd)
                }
                if (toArchive.isEmpty() && docs.isNotEmpty()) {
                    toArchive = docs
                }

                // #region agent log
                val sampleSid = docs.firstOrNull()?.let { d ->
                    d.get("scheduleId")?.let { v -> "${v::class.java.simpleName}:$v" } ?: "null"
                } ?: "no_docs"
                AgentDebugLog.log(
                    "TeacherDashboardFragment:archiveCurrentClassAttendance",
                    "toArchive ready",
                    "H4",
                    mapOf(
                        "runId" to "pre",
                        "totalQueryN" to docs.size,
                        "toArchiveN" to toArchive.size,
                        "scheduleIdParam" to scheduleId,
                        "sampleScheduleIdField" to sampleSid
                    )
                )
                // #endregion

                var archivedCount = 0
                for (doc in toArchive) {
                    val archiveData = doc.data?.toMutableMap() ?: continue
                    archiveData.remove(EXCLUDE_FROM_TEACHER_DASHBOARD)
                    archiveData.remove("excludedFromDashboardAt")
                    archiveData["teacherId"] = currentUser.uid
                    archiveData["scheduleId"] = doc.getString("scheduleId")
                        ?: (doc.get("scheduleId") as? Number)?.toString()
                        ?: scheduleId
                    archiveData["archivedAt"] = System.currentTimeMillis()
                    archiveData["originalId"] = doc.id

                    db.collection("archived_attendance")
                        .document(doc.id)
                        .set(archiveData)
                        .await()

                    doc.reference.delete().await()
                    archivedCount++
                }

                toArchive.forEach { pendingHiddenAttendanceIds.add(it.id) }

                val scannedIds = toArchive.mapNotNull { doc ->
                    doc.getString("studentId") ?: doc.getString("userId")
                }.filter { it.isNotBlank() }.toSet()
                val archiveSection = currentScheduleSection ?: toArchive.firstOrNull()?.getString("section") ?: ""
                createAbsentRecordsForNoShows(scheduleId, subject, archiveSection, currentUser.uid, scannedIds)

                // #region agent log
                AgentDebugLog.log(
                    "TeacherDashboardFragment:archiveCurrentClassAttendance",
                    "before loadRecentAttendance afterEndClass",
                    "H5",
                    mapOf(
                        "runId" to "pre",
                        "archivedCount" to archivedCount,
                        "endedScheduleId" to scheduleId,
                        "listeningAfterNull" to (listeningScheduleId ?: "")
                    )
                )
                // #endregion
                loadRecentAttendance(
                    afterEndClass = true,
                    endedScheduleId = scheduleId,
                    endedSubject = subject
                )
                val endedMsg =
                    if (archivedCount > 0) "Class ended. $archivedCount records archived."
                    else "Class ended."
                Toast.makeText(requireContext(), endedMsg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                sharedDashboard.suppressDashboardAttendanceFromFirestore = false
                sharedDashboard.manuallyEndedSessionKeys.remove(endedSessionKeyForToday(scheduleId))
                uiDocIds.forEach { pendingHiddenAttendanceIds.remove(it) }
                Toast.makeText(requireContext(), "Error archiving attendance: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun archiveEndedClassesAttendance(endedSchedules: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val currentUser = auth.currentUser ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                for (schedule in endedSchedules) {
                    val scheduleId = schedule.id
                    val subject = schedule.getString("subject") ?: continue

                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    val todayStart = com.google.firebase.Timestamp(calendar.time)

                    val attendanceSnapshot = db.collection("attendance")
                        .whereEqualTo("teacherId", currentUser.uid)
                        .whereEqualTo("scheduleId", scheduleId)
                        .whereEqualTo("subject", subject)
                        .whereGreaterThanOrEqualTo("timestamp", todayStart)
                        .get()
                        .await()

                    val scannedIds = mutableSetOf<String>()
                    for (doc in attendanceSnapshot.documents) {
                        val archiveData = doc.data?.toMutableMap() ?: continue
                        archiveData.remove(EXCLUDE_FROM_TEACHER_DASHBOARD)
                        archiveData.remove("excludedFromDashboardAt")
                        archiveData["teacherId"] = currentUser.uid
                        archiveData["scheduleId"] = doc.getString("scheduleId")
                            ?: (doc.get("scheduleId") as? Number)?.toString()
                            ?: scheduleId
                        archiveData["archivedAt"] = System.currentTimeMillis()
                        archiveData["originalId"] = doc.id
                        archiveData["autoArchived"] = true

                        val sid = doc.getString("studentId") ?: doc.getString("userId") ?: ""
                        if (sid.isNotBlank()) scannedIds.add(sid)

                        db.collection("archived_attendance")
                            .document(doc.id)
                            .set(archiveData)
                            .await()

                        doc.reference.delete().await()
                    }

                    val section = schedule.getString("section") ?: ""
                    createAbsentRecordsForNoShows(scheduleId, subject, section, currentUser.uid, scannedIds)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error auto-archiving ended classes: ${e.message}", e)
            }
        }
    }

    private suspend fun createAbsentRecordsForNoShows(
        scheduleId: String,
        subject: String,
        section: String,
        teacherId: String,
        scannedStudentIds: Set<String>
    ) {
        try {
            val target = section.trim().lowercase()
            if (target.isEmpty()) return

            val studentDocs = db.collection("users")
                .whereEqualTo("role", "student")
                .get()
                .await()

            val rosterStudents = studentDocs.documents.filter {
                (it.getString("section") ?: "").trim().lowercase() == target
            }

            val todayCal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val todayStart = com.google.firebase.Timestamp(todayCal.time)

            val existingArchived = db.collection("archived_attendance")
                .whereEqualTo("teacherId", teacherId)
                .whereEqualTo("scheduleId", scheduleId)
                .whereGreaterThanOrEqualTo("timestamp", todayStart)
                .get()
                .await()
            val alreadyRecordedIds = existingArchived.documents.mapNotNull { doc ->
                doc.getString("studentId") ?: doc.getString("userId")
            }.filter { it.isNotBlank() }.toSet()

            val allKnownIds = scannedStudentIds + alreadyRecordedIds

            val now = com.google.firebase.Timestamp.now()
            for (studentDoc in rosterStudents) {
                val studentId = studentDoc.id
                if (studentId in allKnownIds) continue

                val studentName = studentDoc.getString("name") ?: "Unknown"
                val absentData = hashMapOf(
                    "studentId" to studentId,
                    "userId" to studentId,
                    "studentName" to studentName,
                    "teacherId" to teacherId,
                    "scheduleId" to scheduleId,
                    "subject" to subject,
                    "section" to section,
                    "timestamp" to now,
                    "status" to "ABSENT",
                    "location" to "",
                    "notes" to "Auto-marked absent - did not attend",
                    "isAutoAbsent" to true,
                    "sessionId" to "",
                    "archivedAt" to System.currentTimeMillis(),
                    "autoArchived" to true
                )
                db.collection("archived_attendance").add(absentData).await()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error creating absent records: ${e.message}", e)
        }
    }

    private fun scheduleAutoArchiveAtClassEnd(endTime: String) {
        classEndRunnable?.let { classEndHandler.removeCallbacks(it) }
        try {
            val (endHour, endMin) = parseTime24(endTime)
            val now = java.util.Calendar.getInstance()
            val classEnd = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, endHour)
                set(java.util.Calendar.MINUTE, endMin)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val millisUntilEnd = classEnd.timeInMillis - now.timeInMillis

            if (millisUntilEnd > 0) {
                classEndRunnable = Runnable { archiveCurrentClassAttendance() }
                classEndHandler.postDelayed(classEndRunnable!!, millisUntilEnd)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error scheduling auto-archive: ${e.message}", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        removeBothAttendanceListeners()
        listeningScheduleId = null
        listeningSubject = null
        clearRosterSessionContext()
        attendanceAdapter = null
        attendanceList.clear()
        classEndRunnable?.let { classEndHandler.removeCallbacks(it) }
    }
}
