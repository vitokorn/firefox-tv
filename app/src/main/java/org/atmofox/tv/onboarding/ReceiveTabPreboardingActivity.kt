/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("DEPRECATION")

package org.atmofox.tv.onboarding

import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.atmofox.tv.FirefoxApplication
import org.atmofox.tv.MainActivity
import org.atmofox.tv.R
import org.atmofox.tv.telemetry.TelemetryIntegration

/**
 * Manages an onboarding screen, which is shown once to users upon app start in order
 * to educate them about receive tab functionality.
 */
class ReceiveTabPreboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.receive_tab_preboarding)

        val descriptionText = findViewById<TextView>(R.id.descriptionText)
        val buttonSignIn = findViewById<Button>(R.id.buttonSignIn)
        val buttonNotNow = findViewById<Button>(R.id.buttonNotNow)

        descriptionText.text = resources.getString(
            R.string.fxa_preboarding_instruction1,
            resources.getString(R.string.firefox_tv_brand_name_short),
            resources.getString(R.string.firefox_tv_brand_name)
        )

        buttonSignIn.setOnClickListener {
            TelemetryIntegration.INSTANCE.fxaPreboardingSignInButtonClickEvent()
            @Suppress("DEPRECATION") // Couldn't work out a better way to do this. If you
            // think of one, please replace this
            (application as FirefoxApplication).mainActivityCommandBus
                .onNext(MainActivity.Command.BEGIN_LOGIN)
            finish()
        }

        buttonNotNow.setOnClickListener {
            finish()
            TelemetryIntegration.INSTANCE.fxaPreboardingDismissButtonClickEvent()
        }

        setOnboardReceiveTabsShown()
    }

    private fun setOnboardReceiveTabsShown() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(ONBOARD_RECEIVE_TABS_SHOWN_PREF, true)
                .apply()
    }

    companion object {
        const val ONBOARD_RECEIVE_TABS_SHOWN_PREF = "onboard_receive_tabs_shown"
    }
}
