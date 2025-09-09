package com.example.attendancesystem

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import com.example.attendancesystem.databinding.DialogAttendanceConfirmationBinding
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class AttendanceConfirmationDialog(
    context: Context,
    private val subject: String,
    private val timestamp: Timestamp
) : Dialog(context) {

    private lateinit var binding: DialogAttendanceConfirmationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogAttendanceConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setCancelable(false)


        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val date = dateFormat.format(timestamp.toDate())
        val time = timeFormat.format(timestamp.toDate())


        binding.tvConfirmationDetails.text = "Subject: $subject\nDate: $date\nTime: $time"

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }
} 