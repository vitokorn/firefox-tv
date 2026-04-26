/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import org.atmofox.tv.R
import java.lang.ref.WeakReference

object ViewUtils {
    /**
     * Runnable to show the keyboard for a specific view.
     */
    private class ShowKeyboard(view: View) : Runnable {
        companion object {
            private const val INTERVAL_MS = 100
        }

        private val viewReference: WeakReference<View> = WeakReference(view)
        private val handler = Handler(Looper.getMainLooper())
        private var tries = 10

        override fun run() {
            if (tries <= 0) {
                return
            }

            val view = viewReference.get() ?: return

            if (!view.isFocusable || !view.isFocusableInTouchMode) {
                return
            }

            if (!view.requestFocus()) {
                post()
                return
            }

            val activity = view.context as? Activity ?: return

            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return

            if (!imm.isActive(view)) {
                post()
                return
            }

            if (!imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)) {
                post()
            }
        }

        fun post() {
            tries--
            handler.postDelayed(this, INTERVAL_MS.toLong())
        }
    }

    @JvmStatic
    fun showKeyboard(view: View) {
        val showKeyboard = ShowKeyboard(view)
        showKeyboard.post()
    }

    @JvmStatic
    fun hideKeyboard(view: View): Boolean {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return false
        return imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    @JvmStatic
    fun isRTL(view: View): Boolean {
        return ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL
    }

    @JvmStatic
    fun showCenteredTopToast(context: Context, @StringRes resId: Int) {
        showToast(context, resId, "top")
    }

    @JvmStatic
    fun showCenteredBottomToast(context: Context, @StringRes resId: Int) {
        showToast(context, resId, "bottom")
    }

    private fun showToast(context: Context, @StringRes resId: Int, toastLocation: String) {
        val text = context.resources.getString(resId)
        showToast(context, text, toastLocation)
    }

    @JvmStatic
    fun showCenteredBottomToast(context: Context, text: String) {
        showToast(context, text, "bottom")
    }

    private fun showToast(context: Context, text: String, toastLocation: String) {
        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.custom_toast, null)
        val textView = layout.findViewById<TextView>(R.id.toast_text)
        textView.text = text

        val toast = Toast(context)
        toast.duration = Toast.LENGTH_LONG
        toast.view = layout

        if (toastLocation == "top") {
            toast.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 200)
        }
        if (toastLocation == "bottom") {
            toast.setGravity(Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM, 0, 100)
        }
        toast.show()
    }
}
