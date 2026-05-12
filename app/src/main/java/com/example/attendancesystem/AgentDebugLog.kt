package com.example.attendancesystem

import android.content.Context
import android.util.Log

object AgentDebugLog {
    fun log(tag: String, msg: String, level: String = "INFO", data: Map<String, Any?>? = null) {
        Log.d(tag, "[$level] $msg - $data")
    }

    fun appendNdjsonFile(context: Context, tag: String, msg: String, level: String = "INFO", data: Map<String, Any?>? = null) {
        Log.d(tag, "[$level] $msg - $data")
    }
}
