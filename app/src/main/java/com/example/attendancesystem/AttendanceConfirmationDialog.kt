package com.example.attendancesystem

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.attendancesystem.utils.ProfilePictureManager
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceConfirmationDialog : DialogFragment() {

    companion object {
        private const val ARG_STUDENT_NAME = "studentName"
        private const val ARG_SECTION = "section"
        private const val ARG_SUBJECT = "subject"
        private const val ARG_STATUS = "status"
        private const val ARG_TIMESTAMP = "timestamp"

        fun newInstance(
            studentName: String,
            section: String,
            subject: String,
            status: String,
            timestampMillis: Long
        ): AttendanceConfirmationDialog {
            return AttendanceConfirmationDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_STUDENT_NAME, studentName)
                    putString(ARG_SECTION, section)
                    putString(ARG_SUBJECT, subject)
                    putString(ARG_STATUS, status)
                    putLong(ARG_TIMESTAMP, timestampMillis)
                }
            }
        }
    }

    var onDoneListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_AttendanceSystem_FullScreenDialog)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_attendance_confirmation, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setWindowAnimations(android.R.style.Animation_Activity)
            // Dark status bar and nav bar to blend with the design
            statusBarColor = 0xFF219669.toInt()
            navigationBarColor = 0xFF1E1E1E.toInt()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return

        val studentName = args.getString(ARG_STUDENT_NAME, "Student")
        val section = args.getString(ARG_SECTION, "Section")
        val subject = args.getString(ARG_SUBJECT, "Subject")
        val status = args.getString(ARG_STATUS, "PRESENT")
        val timestampMillis = args.getLong(ARG_TIMESTAMP, System.currentTimeMillis())

        val dateObj = Date(timestampMillis)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        // Header status text
        val textConfirmationStatus = view.findViewById<TextView>(R.id.textConfirmationStatus)
        textConfirmationStatus.text = if (status == "LATE") "You arrived late" else "You're on time"

        // Student profile
        val imageProfilePic = view.findViewById<ImageView>(R.id.imageConfirmProfilePic)
        val textInitials = view.findViewById<TextView>(R.id.textConfirmInitials)
        val textStudentName = view.findViewById<TextView>(R.id.textConfirmStudentName)
        val textSection = view.findViewById<TextView>(R.id.textConfirmSection)
        val textStatusBadge = view.findViewById<TextView>(R.id.textConfirmStatusBadge)

        textStudentName.text = studentName
        textSection.text = section.uppercase()

        // Load profile picture or show initials
        ProfilePictureManager.getInstance().loadProfilePicture(
            requireContext(), imageProfilePic, textInitials, studentName, "ST"
        )

        // Status badge
        if (status == "LATE") {
            textStatusBadge.text = "Late"
            textStatusBadge.setTextColor(0xFFBA7517.toInt())
            textStatusBadge.setBackgroundResource(R.drawable.bg_confirmation_status_pill_late)
        } else {
            textStatusBadge.text = "On time"
            textStatusBadge.setTextColor(0xFF27AE60.toInt())
        }

        // Info card data
        view.findViewById<TextView>(R.id.textConfirmSubject).text = subject
        view.findViewById<TextView>(R.id.textConfirmTime).text = timeFormat.format(dateObj)
        view.findViewById<TextView>(R.id.textConfirmDate).text = dateFormat.format(dateObj)
        view.findViewById<TextView>(R.id.textConfirmSectionValue).text = section.uppercase()

        // Done button
        view.findViewById<MaterialButton>(R.id.buttonConfirmDone).setOnClickListener {
            dismiss()
            onDoneListener?.invoke()
        }
    }
}