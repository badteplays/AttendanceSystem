package com.example.attendancesystem

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.attendancesystem.utils.ThemeManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import jxl.Workbook
import jxl.format.Alignment
import jxl.format.BorderLineStyle
import jxl.format.Colour
import jxl.write.Label
import jxl.write.WritableCellFormat
import jxl.write.WritableFont
import jxl.write.WritableWorkbook
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TeacherOptionsFragment : Fragment() {
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var currentName: String = "Teacher"

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                try {
                    val profileManager = com.example.attendancesystem.utils.ProfilePictureManager.getInstance()
                    val success = profileManager.saveProfilePicture(requireContext(), uri)
                    if (success) {
                        Toast.makeText(requireContext(), "Profile picture updated", Toast.LENGTH_SHORT).show()
                        val imageView = view?.findViewById<ImageView>(R.id.imageProfilePic)
                        val initialsView = view?.findViewById<TextView>(R.id.textInitials)
                        if (imageView != null && initialsView != null) {
                            profileManager.loadProfilePicture(requireContext(), imageView, initialsView, currentName, "TC")
                        }
                        (activity as? TeacherMainActivity)?.refreshNavHeader()
                    } else {
                        Toast.makeText(requireContext(), "Failed to save profile picture", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_teacher_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<LinearLayout>(R.id.buttonTheme)?.setOnClickListener { showThemePicker() }
        view.findViewById<LinearLayout>(R.id.buttonAboutUs)?.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://badteplays.github.io/FPL-WEBSITE/")))
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Unable to open link", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<LinearLayout>(R.id.buttonLogout)?.setOnClickListener { confirmLogout() }
        view.findViewById<LinearLayout>(R.id.buttonExportExcel)?.setOnClickListener { exportToExcel() }
        view.findViewById<LinearLayout>(R.id.buttonChangeProfile)?.setOnClickListener { openImagePicker() }
        view.findViewById<View>(R.id.btnEditAvatar)?.setOnClickListener { openImagePicker() }
        view.findViewById<ImageView>(R.id.imageProfilePic)?.setOnClickListener { openImagePicker() }
        view.findViewById<TextView>(R.id.textInitials)?.setOnClickListener { openImagePicker() }

        val themeManager = ThemeManager.getInstance(requireContext())
        view.findViewById<TextView>(R.id.textCurrentTheme)?.text = themeManager.getThemeName(themeManager.getCurrentTheme())

        loadUserData(view)
    }

    private fun loadUserData(view: View) {
        val textName = view.findViewById<TextView>(R.id.textUserName)
        val textEmail = view.findViewById<TextView>(R.id.textUserEmail)
        val imageProfile = view.findViewById<ImageView>(R.id.imageProfilePic)
        val textInitials = view.findViewById<TextView>(R.id.textInitials)
        val badgeRole = view.findViewById<TextView>(R.id.badgeRole)
        val badgeDept = view.findViewById<TextView>(R.id.badgeDept)

        val currentUser = auth.currentUser ?: return
        textEmail?.text = currentUser.email ?: "No email"

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUser.uid)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded) return@addSnapshotListener
                currentName = snapshot?.getString("name") ?: (currentUser.displayName ?: "Teacher")
                val department = snapshot?.getString("department") ?: ""
                textName?.text = currentName
                badgeRole?.text = "Teacher"
                badgeDept?.text = department.ifBlank { "Department" }
                try {
                    val profileManager = com.example.attendancesystem.utils.ProfilePictureManager.getInstance()
                    profileManager.loadProfilePicture(requireContext(), imageProfile ?: return@addSnapshotListener, textInitials ?: return@addSnapshotListener, currentName, "TC")
                } catch (_: Exception) { }
            }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun showThemePicker() {
        val themeManager = ThemeManager.getInstance(requireContext())
        val themes = arrayOf("Light", "Dark", "System default")
        val current = themeManager.getCurrentTheme()
        AlertDialog.Builder(requireContext())
            .setTitle("Choose theme")
            .setSingleChoiceItems(themes, current) { dialog, which ->
                val mode = when (which) {
                    0 -> ThemeManager.THEME_LIGHT
                    1 -> ThemeManager.THEME_DARK
                    else -> ThemeManager.THEME_SYSTEM
                }
                themeManager.setTheme(mode)
                view?.findViewById<TextView>(R.id.textCurrentTheme)?.text = themeManager.getThemeName(mode)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Log out?")
            .setMessage("You will need to sign in again to access your account.")
            .setPositiveButton("Yes, log out") { _, _ ->
                val prefs = requireContext().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("explicit_logout", true).apply()
                auth.signOut()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportToExcel() {
        val uid = auth.currentUser?.uid ?: return
        Toast.makeText(requireContext(), "Preparing export…", Toast.LENGTH_SHORT).show()

        val db = FirebaseFirestore.getInstance()
        db.collection("schedules").whereEqualTo("teacherId", uid).get()
            .addOnSuccessListener { schedSnap ->
                val subjects = schedSnap.documents.mapNotNull { it.getString("subject") }.distinct()
                if (subjects.isEmpty()) {
                    Toast.makeText(requireContext(), "No schedules found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val sections = schedSnap.documents.mapNotNull { it.getString("section")?.trim()?.lowercase() }.distinct()
                val sectionScheduleMap = mutableMapOf<String, MutableSet<String>>()
                for (doc in schedSnap.documents) {
                    val subj = doc.getString("subject") ?: continue
                    val sec = (doc.getString("section") ?: "").trim().lowercase()
                    sectionScheduleMap.getOrPut("$subj||$sec") { mutableSetOf() }
                }

                db.collection("users").whereEqualTo("role", "student").get()
                    .addOnSuccessListener { studentSnap ->
                        val rosterBySection = mutableMapOf<String, MutableList<String>>()
                        for (doc in studentSnap.documents) {
                            val sec = (doc.getString("section") ?: "").trim().lowercase()
                            if (sec in sections) {
                                val name = doc.getString("name") ?: "Unknown"
                                rosterBySection.getOrPut(sec) { mutableListOf() }.add(name)
                            }
                        }

                db.collection("attendance").whereEqualTo("teacherId", uid).get()
                    .addOnSuccessListener { activeSnap ->
                        db.collection("archived_attendance").whereEqualTo("teacherId", uid).get()
                            .addOnSuccessListener { archSnap ->
                                if (!isAdded) return@addOnSuccessListener
                                val allDocs = (activeSnap.documents + archSnap.documents).filter { doc ->
                                    val excl = doc.getBoolean("excludeFromTeacherDashboard") == true ||
                                        (doc.get("excludeFromTeacherDashboard") as? Long) == 1L ||
                                        (doc.get("excludeFromTeacherDashboard") as? String).equals("true", true)
                                    !excl && subjects.contains(doc.getString("subject"))
                                }
                                buildAndSaveExcel(allDocs, rosterBySection)
                            }
                            .addOnFailureListener {
                                if (!isAdded) return@addOnFailureListener
                                val allDocs = activeSnap.documents.filter { doc ->
                                    subjects.contains(doc.getString("subject"))
                                }
                                buildAndSaveExcel(allDocs, rosterBySection)
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Failed to load attendance", Toast.LENGTH_SHORT).show()
                    }
                    }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load schedules", Toast.LENGTH_SHORT).show()
            }
    }

    private data class ExportRecord(
        val studentName: String, val subject: String,
        val section: String, val status: String, val timestamp: Date
    )

    private fun buildAndSaveExcel(docs: List<DocumentSnapshot>, rosterBySection: Map<String, List<String>> = emptyMap()) {
        val records = docs.mapNotNull { doc ->
            val ts = doc.getTimestamp("timestamp")?.toDate() ?: return@mapNotNull null
            ExportRecord(
                doc.getString("studentName") ?: "", doc.getString("subject") ?: "",
                doc.getString("section") ?: "", doc.getString("status") ?: "PRESENT", ts
            )
        }

        if (records.isEmpty()) {
            Toast.makeText(requireContext(), "No attendance data to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "Attendance_Report_$ts.xls"
            val dateFmt       = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val monthKeyFmt   = SimpleDateFormat("yyyy-MM",    Locale.getDefault())
            val monthLongFmt  = SimpleDateFormat("MMMM yyyy",  Locale.getDefault())
            val monthShortFmt = SimpleDateFormat("MMM yy",     Locale.getDefault())
            val shortDateFmt  = SimpleDateFormat("M/d",        Locale.getDefault())
            val dayAbbrFmt    = SimpleDateFormat("EEE",        Locale.getDefault())
            val generatedAt   = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date())

            val exportDir = File(requireContext().cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, filename)
            val workbook: WritableWorkbook = Workbook.createWorkbook(file)

            // ── helpers ────────────────────────────────────────────────────────
            fun allBorders(fmt: WritableCellFormat) {
                fmt.setBorder(jxl.format.Border.ALL, BorderLineStyle.THIN, Colour.GREY_25_PERCENT)
            }

            fun isWeekend(dateStr: String): Boolean = try {
                val cal = Calendar.getInstance()
                cal.time = dateFmt.parse(dateStr)!!
                cal.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
            } catch (_: Exception) { false }

            fun statusLabel(raw: String): String = when (raw.uppercase()) {
                "PRESENT"  -> "P"
                "LATE"     -> "T"
                "EXCUSED"  -> "E"
                else       -> "A"   // ABSENT / CUTTING
            }

            fun statusPriority(raw: String): Int = when (raw.uppercase()) {
                "PRESENT" -> 0; "LATE" -> 1; "EXCUSED" -> 2; else -> 3
            }

            fun statusCategory(raw: String): String = when (raw.uppercase()) {
                "PRESENT"            -> "PRESENT"
                "LATE"               -> "LATE"
                "EXCUSED"            -> "EXCUSED"
                else                 -> "ABSENT"
            }

            // ── cell formats ───────────────────────────────────────────────────
            val titleFont = WritableFont(WritableFont.ARIAL, 13, WritableFont.BOLD)
            titleFont.colour = Colour.WHITE
            val titleFmt = WritableCellFormat(titleFont).apply {
                setBackground(Colour.DARK_BLUE)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }

            val subtitleFont = WritableFont(WritableFont.ARIAL, 9)
            subtitleFont.colour = Colour.GREY_50_PERCENT
            val subtitleFmt = WritableCellFormat(subtitleFont)

            val headerFont = WritableFont(WritableFont.ARIAL, 10, WritableFont.BOLD)
            headerFont.colour = Colour.WHITE
            val headerFmt = WritableCellFormat(headerFont).apply {
                setBackground(Colour.DARK_BLUE)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
                setWrap(true)
            }
            allBorders(headerFmt)

            val headerLeftFmt = WritableCellFormat(headerFont).apply {
                setBackground(Colour.DARK_BLUE)
                setAlignment(Alignment.LEFT)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(headerLeftFmt)

            // Weekend column header  (grey)
            val wkndHdrFont = WritableFont(WritableFont.ARIAL, 9, WritableFont.BOLD)
            wkndHdrFont.colour = Colour.GREY_50_PERCENT
            val wkndHdrFmt = WritableCellFormat(wkndHdrFont).apply {
                setBackground(Colour.GREY_25_PERCENT)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
                setWrap(true)
            }
            allBorders(wkndHdrFmt)

            // Weekend data cell (grey)
            val wkndCellFmt = WritableCellFormat().apply {
                setBackground(Colour.GREY_25_PERCENT)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(wkndCellFmt)

            val sectionHdrFont = WritableFont(WritableFont.ARIAL, 11, WritableFont.BOLD)
            sectionHdrFont.colour = Colour.DARK_BLUE
            val sectionHdrFmt = WritableCellFormat(sectionHdrFont)

            val sectionSubFont = WritableFont(WritableFont.ARIAL, 9)
            sectionSubFont.colour = Colour.GREY_50_PERCENT
            val sectionSubFmt = WritableCellFormat(sectionSubFont)

            val noFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 10)).apply {
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(noFmt)

            val nameFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 10)).apply {
                setAlignment(Alignment.LEFT)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(nameFmt)

            val presentFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 9, WritableFont.BOLD)).apply {
                setBackground(Colour.LIGHT_GREEN)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(presentFmt)

            val lateFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 9, WritableFont.BOLD)).apply {
                setBackground(Colour.LIGHT_ORANGE)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(lateFmt)

            val absentFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 9, WritableFont.BOLD)).apply {
                setBackground(Colour.ROSE)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(absentFmt)

            val excusedFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 9, WritableFont.BOLD)).apply {
                setBackground(Colour.LIGHT_BLUE)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(excusedFmt)

            val emptyFmt = WritableCellFormat().apply {
                setBackground(Colour.WHITE)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(emptyFmt)

            val numFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 10)).apply {
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(numFmt)

            val rateFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 10, WritableFont.BOLD)).apply {
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(rateFmt)

            val totalLabelFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 10, WritableFont.BOLD)).apply {
                setBackground(Colour.GREY_25_PERCENT)
                setAlignment(Alignment.LEFT)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(totalLabelFmt)

            val totalNumFmt = WritableCellFormat(WritableFont(WritableFont.ARIAL, 10, WritableFont.BOLD)).apply {
                setBackground(Colour.GREY_25_PERCENT)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            allBorders(totalNumFmt)

            // Yellow legend bar
            val legendFont = WritableFont(WritableFont.ARIAL, 9, WritableFont.BOLD)
            val legendFmt = WritableCellFormat(legendFont).apply {
                setBackground(Colour.YELLOW)
                setAlignment(Alignment.LEFT)
                setVerticalAlignment(jxl.format.VerticalAlignment.CENTRE)
            }
            val legendEmptyFmt = WritableCellFormat().apply {
                setBackground(Colour.YELLOW)
            }

            // ── Choice 2: group by Subject → Month ─────────────────────────────
            val bySubject = records.groupBy { it.subject }
            var sheetIdx = 0

            for ((subject, subjectRecs) in bySubject.entries.sortedBy { it.key }) {

                // Group this subject's records by YEAR-MONTH  (e.g. "2026-02")
                val byMonth = subjectRecs.groupBy { monthKeyFmt.format(it.timestamp) }

                for ((yearMonth, monthRecs) in byMonth.entries.sortedBy { it.key }) {
                    val monthDate      = monthKeyFmt.parse(yearMonth)!!
                    val monthLong      = monthLongFmt.format(monthDate).uppercase()  // "FEBRUARY 2026"
                    val monthShort     = monthShortFmt.format(monthDate)            // "Feb 26"

                    // Sheet name ≤ 31 chars, no forbidden chars
                    val rawName  = "${subject.take(19)} - $monthShort"
                    val safeName = rawName.take(31).replace("[\\/*?\\[\\]:]".toRegex(), "_")
                    val sheet    = workbook.createSheet(safeName, sheetIdx++)

                    // Determine total date columns for this sheet (max across sections)
                    val allDatesThisSheet = monthRecs
                        .map { dateFmt.format(it.timestamp) }.distinct().sorted()
                    val totalDateCols = allDatesThisSheet.size
                    // col layout:  0=NO  1=NAME  2..2+dates-1=dates  then P/T/A/Total/Rate
                    val colP     = totalDateCols + 2
                    val colT     = totalDateCols + 3
                    val colA     = totalDateCols + 4
                    val colTotal = totalDateCols + 5
                    val colRate  = totalDateCols + 6
                    val totalCols = colRate + 1

                    // ── Title row (row 0) spanning all columns ──────────────────
                    val titleText = "$subject SUMMARY OF ATTENDANCE ($monthLong)"
                    sheet.addCell(Label(0, 0, titleText, titleFmt))
                    for (c in 1 until totalCols) sheet.addCell(Label(c, 0, "", titleFmt))
                    sheet.addCell(Label(0, 1, "Generated: $generatedAt", subtitleFmt))

                    // ── Sections stacked vertically ─────────────────────────────
                    val sectionGroups = monthRecs.groupBy { it.section }
                    var currentRow = 3

                    for ((section, sRecs) in sectionGroups.entries.sortedBy { it.key }) {
                        val dates      = sRecs.map { dateFmt.format(it.timestamp) }.distinct().sorted()
                        val students   = sRecs.map { it.studentName }.distinct().sorted()
                        val sectionKey = section.trim().lowercase()
                        val rosterNames  = rosterBySection[sectionKey] ?: emptyList()
                        val rosterCount  = if (rosterNames.isNotEmpty()) rosterNames.size else students.size

                        // Section label
                        sheet.addCell(Label(0, currentRow,
                            "Section: ${section.uppercase()}     Enrolled: $rosterCount     Sessions: ${dates.size}",
                            sectionHdrFmt))
                        currentRow++

                        // Column header row
                        val headerRow = currentRow
                        sheet.addCell(Label(0, headerRow, "NO.",  headerFmt))
                        sheet.addCell(Label(1, headerRow, "NAME", headerLeftFmt))

                        // Date header columns with weekend detection
                        dates.forEachIndexed { i, d ->
                            val shortDate = try { shortDateFmt.format(dateFmt.parse(d)!!) } catch (_: Exception) { d }
                            val dayAbbr   = try { dayAbbrFmt.format(dateFmt.parse(d)!!).uppercase() } catch (_: Exception) { "" }
                            val weekend   = isWeekend(d)
                            val label     = if (weekend) "$shortDate\n$dayAbbr" else shortDate
                            sheet.addCell(Label(i + 2, headerRow, label, if (weekend) wkndHdrFmt else headerFmt))
                        }

                        sheet.addCell(Label(colP,     headerRow, "P",     headerFmt))
                        sheet.addCell(Label(colT,     headerRow, "T",     headerFmt))
                        sheet.addCell(Label(colA,     headerRow, "A",     headerFmt))
                        sheet.addCell(Label(colTotal, headerRow, "Total", headerFmt))
                        sheet.addCell(Label(colRate,  headerRow, "Rate",  headerFmt))
                        currentRow++

                        // Student rows
                        students.forEachIndexed { idx, student ->
                            sheet.addCell(Number(0, currentRow, (idx + 1).toDouble(), noFmt))
                            sheet.addCell(Label(1, currentRow, student, nameFmt))

                            val stuRecs = sRecs.filter { it.studentName == student }
                            var pCnt = 0; var tCnt = 0; var aCnt = 0

                            dates.forEachIndexed { di, date ->
                                val weekend = isWeekend(date)
                                val dayRecs = stuRecs.filter { dateFmt.format(it.timestamp) == date }
                                val rec     = dayRecs.sortedBy { statusPriority(it.status) }.firstOrNull()
                                if (rec != null) {
                                    val cat     = statusCategory(rec.status)
                                    val display = statusLabel(rec.status)
                                    when (cat) {
                                        "PRESENT" -> { pCnt++; sheet.addCell(Label(di + 2, currentRow, display, presentFmt)) }
                                        "LATE"    -> { tCnt++; sheet.addCell(Label(di + 2, currentRow, display, lateFmt)) }
                                        "EXCUSED" -> { aCnt++; sheet.addCell(Label(di + 2, currentRow, display, excusedFmt)) }
                                        else      -> { aCnt++; sheet.addCell(Label(di + 2, currentRow, display, absentFmt)) }
                                    }
                                } else {
                                    sheet.addCell(Label(di + 2, currentRow, "", if (weekend) wkndCellFmt else emptyFmt))
                                }
                            }

                            val totalS = pCnt + tCnt + aCnt
                            val rate   = if (totalS > 0) (pCnt + tCnt).toFloat() / totalS * 100 else 0f
                            sheet.addCell(Number(colP,     currentRow, pCnt.toDouble(),   numFmt))
                            sheet.addCell(Number(colT,     currentRow, tCnt.toDouble(),   numFmt))
                            sheet.addCell(Number(colA,     currentRow, aCnt.toDouble(),   numFmt))
                            sheet.addCell(Number(colTotal, currentRow, totalS.toDouble(), numFmt))
                            sheet.addCell(Label(colRate,   currentRow, String.format("%.1f%%", rate), rateFmt))
                            currentRow++
                        }

                        // Totals row for this section
                        currentRow++
                        sheet.addCell(Label(0, currentRow, "", totalLabelFmt))
                        sheet.addCell(Label(1, currentRow, "Totals — ${section.uppercase()}", totalLabelFmt))
                        dates.forEachIndexed { di, date ->
                            val weekend = isWeekend(date)
                            val dayRecs = sRecs.filter { dateFmt.format(it.timestamp) == date }
                            val deduped = dayRecs.groupBy { it.studentName }
                                .map { (_, r) -> r.sortedBy { statusPriority(it.status) }.first() }
                            val p = deduped.count { statusCategory(it.status) == "PRESENT" }
                            val t = deduped.count { statusCategory(it.status) == "LATE" }
                            val a = deduped.count { statusCategory(it.status) == "ABSENT" || statusCategory(it.status) == "EXCUSED" }
                            sheet.addCell(Label(di + 2, currentRow,
                                if (weekend) "" else "${p}P/${t}T/${a}A",
                                if (weekend) wkndCellFmt else totalNumFmt))
                        }
                        val allDeduped   = sRecs.groupBy { "${it.studentName}|${dateFmt.format(it.timestamp)}" }
                            .map { (_, r) -> r.sortedBy { statusPriority(it.status) }.first() }
                        val allP    = allDeduped.count { statusCategory(it.status) == "PRESENT" }
                        val allT    = allDeduped.count { statusCategory(it.status) == "LATE" }
                        val allA    = allDeduped.count { statusCategory(it.status) == "ABSENT" || statusCategory(it.status) == "EXCUSED" }
                        val allTot  = allP + allT + allA
                        val allRate = if (allTot > 0) (allP + allT).toFloat() / allTot * 100 else 0f
                        sheet.addCell(Number(colP,     currentRow, allP.toDouble(),   totalNumFmt))
                        sheet.addCell(Number(colT,     currentRow, allT.toDouble(),   totalNumFmt))
                        sheet.addCell(Number(colA,     currentRow, allA.toDouble(),   totalNumFmt))
                        sheet.addCell(Number(colTotal, currentRow, allTot.toDouble(), totalNumFmt))
                        sheet.addCell(Label(colRate,   currentRow, String.format("%.1f%%", allRate), totalNumFmt))

                        currentRow += 3   // blank gap before next section
                    }

                    // ── Legend row ──────────────────────────────────────────────
                    sheet.addCell(Label(0, currentRow,
                        "  E – Excused / Pulled-out     A – Absent     T – Late     P – Present",
                        legendFmt))
                    for (c in 1 until totalCols) sheet.addCell(Label(c, currentRow, "", legendEmptyFmt))

                    // ── Column widths ────────────────────────────────────────────
                    sheet.setColumnView(0, 6)   // NO.
                    sheet.setColumnView(1, 30)  // NAME
                    for (i in 2 until totalDateCols + 2) sheet.setColumnView(i, 7)
                    sheet.setColumnView(colP,     8)
                    sheet.setColumnView(colT,     8)
                    sheet.setColumnView(colA,     8)
                    sheet.setColumnView(colTotal, 8)
                    sheet.setColumnView(colRate,  9)
                }
            }

            // ── Overview sheet (all subjects × months × sections) ─────────────
            val summSheet = workbook.createSheet("Overview", sheetIdx)
            summSheet.addCell(Label(0, 0, "Attendance Report — Overview", titleFmt))
            summSheet.addCell(Label(0, 1, "Generated: $generatedAt", subtitleFmt))

            val ovHeaders = listOf("Subject", "Section", "Month", "Enrolled", "Sessions", "P", "T", "A", "Rate")
            ovHeaders.forEachIndexed { i, t ->
                summSheet.addCell(Label(i, 3, t, if (i <= 2) headerLeftFmt else headerFmt))
            }

            var ri = 4
            for ((subj, subjectRecs) in bySubject.entries.sortedBy { it.key }) {
                val byMonth2 = subjectRecs.groupBy { monthKeyFmt.format(it.timestamp) }
                for ((ym, mRecs) in byMonth2.entries.sortedBy { it.key }) {
                    val mLabel = monthLongFmt.format(monthKeyFmt.parse(ym)!!)
                    val secGroups2 = mRecs.groupBy { it.section }
                    for ((sec, sRecs) in secGroups2.entries.sortedBy { it.key }) {
                        val secKey    = sec.trim().lowercase()
                        val rosterL   = rosterBySection[secKey]
                        val stu       = if (!rosterL.isNullOrEmpty()) rosterL.size else sRecs.map { it.studentName }.distinct().size
                        val ses       = sRecs.map { dateFmt.format(it.timestamp) }.distinct().size
                        val deduped   = sRecs.groupBy { "${it.studentName}|${dateFmt.format(it.timestamp)}" }
                            .map { (_, r) -> r.sortedBy { statusPriority(it.status) }.first() }
                        val p    = deduped.count { statusCategory(it.status) == "PRESENT" }
                        val t    = deduped.count { statusCategory(it.status) == "LATE" }
                        val a    = deduped.count { statusCategory(it.status) == "ABSENT" || statusCategory(it.status) == "EXCUSED" }
                        val tot  = p + t + a
                        val rate = if (tot > 0) (p + t).toFloat() / tot * 100 else 0f
                        summSheet.addCell(Label(0, ri, subj,          nameFmt))
                        summSheet.addCell(Label(1, ri, sec.uppercase(), nameFmt))
                        summSheet.addCell(Label(2, ri, mLabel,        nameFmt))
                        summSheet.addCell(Number(3, ri, stu.toDouble(), numFmt))
                        summSheet.addCell(Number(4, ri, ses.toDouble(), numFmt))
                        summSheet.addCell(Number(5, ri, p.toDouble(),   numFmt))
                        summSheet.addCell(Number(6, ri, t.toDouble(),   numFmt))
                        summSheet.addCell(Number(7, ri, a.toDouble(),   numFmt))
                        summSheet.addCell(Label(8, ri, String.format("%.1f%%", rate), rateFmt))
                        ri++
                    }
                }
            }
            summSheet.setColumnView(0, 24)
            summSheet.setColumnView(1, 16)
            summSheet.setColumnView(2, 18)
            for (i in 3..8) summSheet.setColumnView(i, 12)

            workbook.write()
            workbook.close()

            saveAndShare(file, filename)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAndShare(sourceFile: File, filename: String) {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.ms-excel")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            uri?.let {
                ctx.contentResolver.openOutputStream(it)?.use { os ->
                    sourceFile.inputStream().use { input -> input.copyTo(os) }
                }
                Toast.makeText(ctx, "Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
                shareFile(it, filename)
            } ?: Toast.makeText(ctx, "Failed to create file", Toast.LENGTH_SHORT).show()
        } else {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", sourceFile)
            Toast.makeText(ctx, "Export ready: $filename", Toast.LENGTH_LONG).show()
            shareFile(uri, filename)
        }
    }

    private fun shareFile(uri: Uri, filename: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Attendance Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share $filename"))
    }
}
