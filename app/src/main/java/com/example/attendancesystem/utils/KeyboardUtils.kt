package com.example.attendancesystem.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

object KeyboardUtils {
    
    fun hideKeyboard(activity: Activity) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = activity.currentFocus ?: View(activity)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    fun showKeyboard(editText: EditText) {
        editText.requestFocus()
        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
    
    fun setupKeyboardDismissOnOutsideTouch(activity: Activity, rootView: View) {
        rootView.setOnTouchListener { _, _ ->
            hideKeyboard(activity)
            false
        }
    }
}
