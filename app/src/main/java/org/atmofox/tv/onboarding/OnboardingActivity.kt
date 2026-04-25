/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("DEPRECATION")

package org.atmofox.tv.onboarding

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.atmofox.tv.R
import org.atmofox.tv.ext.serviceLocator

class OnboardingActivity : AppCompatActivity() {

    private val enableTurboModeButton: Button by lazy { findViewById(R.id.enable_turbo_mode) }
    private val disableTurboModeButton: Button by lazy { findViewById(R.id.disable_turbo_mode) }
    private val onboardingMainText: TextView by lazy { findViewById(R.id.onboarding_main_text) }
    private val turboModeTitle: TextView by lazy { findViewById(R.id.turbo_mode_title) }
    private val turboImageView: ImageView by lazy { findViewById(R.id.turbo_image_view) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        setContent()

        enableTurboModeButton.setOnClickListener {
            setTurboMode(true)
            finish()
        }

        disableTurboModeButton.setOnClickListener {
            setTurboMode(false)
            setResult(Activity.RESULT_OK, Intent())
            finish()
        }

        setOnboardShown()
    }

    private fun setContent() {
        val content = serviceLocator.experimentsProvider.getTurboModeOnboarding()

        disableTurboModeButton.text = resources.getString(content.disableButtonTextId)
        enableTurboModeButton.text = resources.getString(content.enableButtonTextId)
        onboardingMainText.text = resources.getString(content.descriptionId)
        turboModeTitle.text = resources.getString(content.titleId)
        turboImageView.setImageResource(content.imageId)
        turboImageView.contentDescription = resources.getString(content.imageContentDescriptionId)
    }

    private fun setTurboMode(turboModeEnabled: Boolean) {
        serviceLocator.turboMode.isEnabled = turboModeEnabled
    }

    private fun setOnboardShown() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(ONBOARD_SHOWN_PREF, true)
                .apply()
    }

    companion object {
        const val ONBOARD_SHOWN_PREF = "onboard_shown"
    }
}
