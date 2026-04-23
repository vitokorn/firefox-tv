/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.appcompat.widget.SwitchCompat
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.sentry.Sentry
import org.mozilla.tv.firefox.R
import org.mozilla.tv.firefox.architecture.FirefoxViewModelProviders
import org.mozilla.tv.firefox.channels.SettingsScreen
import org.mozilla.tv.firefox.channels.SettingsTile
import org.mozilla.tv.firefox.ext.serviceLocator
import org.mozilla.tv.firefox.fxa.FxaRepo
import org.mozilla.tv.firefox.telemetry.TelemetryIntegration
import org.mozilla.tv.firefox.utils.PicassoWrapper
import org.mozilla.tv.firefox.utils.RoundCornerTransformation
import org.mozilla.tv.firefox.utils.ServiceLocator

const val KEY_SETTINGS_TYPE = "KEY_SETTINGS_TYPE"

/** The settings for the app. */
class SettingsFragment : Fragment() {
    enum class Action {
        SESSION_CLEARED
    }

    var compositeDisposable = CompositeDisposable()
    private lateinit var serviceLocator: ServiceLocator

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        serviceLocator = context!!.serviceLocator

        val settingsVM = FirefoxViewModelProviders.of(this@SettingsFragment).get(SettingsViewModel::class.java)
        val type: SettingsTile = SettingsScreen.valueOf(arguments!!.getString(KEY_SETTINGS_TYPE)!!)
        val view = when (type) {
            SettingsScreen.DATA_COLLECTION -> setupDataCollectionScreen(inflater, container, settingsVM)
            SettingsScreen.CLEAR_COOKIES -> setupClearCookiesScreen(inflater, container, settingsVM)
            SettingsScreen.FXA_PROFILE -> setupFxaProfileScreen(inflater, container)
            else -> {
                Sentry.capture(IllegalStateException("Unexpected Settings type received: $type"))
                return container!!
            }
        }
        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            serviceLocator.screenController.handleBack(fragmentManager!!)
        }

        return view
    }

    private fun setupDataCollectionScreen(
        inflater: LayoutInflater,
        parentView: ViewGroup?,
        settingsViewModel: SettingsViewModel
    ): View {
        val view = inflater.inflate(R.layout.settings_screen_switch, parentView, false)
        val toggle = view.findViewById<SwitchCompat>(R.id.toggle)
        val description = view.findViewById<TextView>(R.id.description)
        settingsViewModel.dataCollectionEnabled.observe(viewLifecycleOwner, Observer<Boolean> { state ->
            toggle.isChecked = state ?: return@Observer
        })
        toggle.setOnClickListener {
            settingsViewModel.setDataCollectionEnabled(toggle.isChecked)
        }
        description.text = resources.getString(R.string.settings_telemetry_description,
                resources.getString(R.string.firefox_tv_brand_name))
        return view
    }

    private fun setupClearCookiesScreen(
        inflater: LayoutInflater,
        parentView: ViewGroup?,
        settingsViewModel: SettingsViewModel
    ): View {
        settingsViewModel.events.observe(viewLifecycleOwner, Observer {
            it?.consume { event ->
                when (event) {
                    Action.SESSION_CLEARED -> {
                        activity?.recreate()
                    }
                }
                true
            }
        })

        val view = inflater.inflate(R.layout.settings_screen_buttons, parentView, false)
        val confirmAction = view.findViewById<Button>(R.id.confirm_action)
        val cancelAction = view.findViewById<Button>(R.id.cancel_action)
        confirmAction.setOnClickListener {
            settingsViewModel.clearBrowsingData(serviceLocator.engineViewCache)
            serviceLocator.screenController.handleBack(fragmentManager!!)
        }
        cancelAction.setOnClickListener {
            serviceLocator.screenController.handleBack(fragmentManager!!)
        }
        return view
    }

    private fun setupFxaProfileScreen(
        inflater: LayoutInflater,
        parentView: ViewGroup?
    ): View {
        val view = inflater.inflate(R.layout.settings_screen_fxa_profile, parentView, false)
        val buttonFirefoxTabs = view.findViewById<Button>(R.id.buttonFirefoxTabs)

        setupFxaText(view)
        setupFxaProfileClickListeners(view)
        observeFxaProfile(view)
            .forEach { compositeDisposable.add(it) }

        val fxaRepo = serviceLocator.fxaRepo
        buttonFirefoxTabs.setOnClickListener {
            fxaRepo.showFxaOnboardingScreen(context!!)
        }

        return view
    }

    private fun setupFxaText(view: View) {
        val appName = resources.getString(R.string.app_name)
        val buttonFirefoxTabs = view.findViewById<Button>(R.id.buttonFirefoxTabs)
        val signedInAs = view.findViewById<TextView>(R.id.signedInAs)
        buttonFirefoxTabs.text = resources.getString(R.string.fxa_settings_primary_button, appName)
        // Username is positioned and styled differently, so it is left blank here
        // and set on another TextView
        signedInAs.text = resources.getString(R.string.fxa_settings_body, "")
    }

    private fun setupFxaProfileClickListeners(view: View) {
        val screenController = serviceLocator.screenController
        val fxaRepo = serviceLocator.fxaRepo
        val telemetryIntegration = TelemetryIntegration.INSTANCE
        val buttonFirefoxTabs = view.findViewById<Button>(R.id.buttonFirefoxTabs)
        val buttonSignOut = view.findViewById<Button>(R.id.buttonSignOut)
        val backButton = view.findViewById<ImageButton>(R.id.backButton)

        buttonFirefoxTabs.setOnClickListener {
            // TODO show send tab tutorial
            telemetryIntegration.fxaProfileShowOnboardingButtonClickEvent()
        }
        buttonSignOut.setOnClickListener {
            fxaRepo.logout()
            screenController.handleBack(fragmentManager!!)
            telemetryIntegration.fxaProfileSignOutButtonClickEvent()
        }
        backButton.setOnClickListener {
            screenController.handleBack(fragmentManager!!)
        }
    }

    private fun observeFxaProfile(view: View): List<Disposable> {
        val accountState = context!!.serviceLocator.fxaRepo.accountState
        val userDisplayName = view.findViewById<TextView>(R.id.userDisplayName)
        val signedInAs = view.findViewById<TextView>(R.id.signedInAs)
        val avatarImage = view.findViewById<ImageView>(R.id.avatarImage)

        return listOf(
            accountState
                .ofType(FxaRepo.AccountState.AuthenticatedWithProfile::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    userDisplayName.text = it.profile.displayName
                    it.profile.avatarSetStrategy
                        .setTransformation(RoundCornerTransformation(avatarImage.width.toFloat()))
                        .invoke(avatarImage)
                },
            accountState
                .filter { it::class.java != FxaRepo.AccountState.AuthenticatedWithProfile::class.java }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    userDisplayName.text = ""
                    signedInAs.text = resources.getString(R.string.fxa_settings_body_no_display_name)
                    PicassoWrapper.client.load(R.drawable.ic_default_avatar).into(avatarImage)
                }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear()
    }

    companion object {
        const val FRAGMENT_TAG = "settings"

        fun newInstance(type: SettingsScreen): SettingsFragment {
            return SettingsFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_SETTINGS_TYPE, type.toString())
                }
            }
        }
    }
}
