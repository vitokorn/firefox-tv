/* -*- Mode: Java; c-basic-offset: 4; tab-width: 4; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.addTo
import io.sentry.Sentry
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.EngineView
import mozilla.components.support.base.observer.Consumable
import mozilla.components.support.utils.toSafeIntent
import org.mozilla.tv.firefox.components.locale.LocaleAwareAppCompatActivity
import org.mozilla.tv.firefox.components.locale.LocaleManager
import org.mozilla.tv.firefox.ext.application
import org.mozilla.tv.firefox.ext.resetView
import org.mozilla.tv.firefox.ext.serviceLocator
import org.mozilla.tv.firefox.ext.setupForApp
import org.mozilla.tv.firefox.ext.webRenderComponents
import org.mozilla.tv.firefox.fxa.FxaReceivedTab
import org.mozilla.tv.firefox.onboarding.OnboardingActivity
import org.mozilla.tv.firefox.telemetry.TelemetryIntegration
import org.mozilla.tv.firefox.telemetry.UrlTextInputLocation
import org.mozilla.tv.firefox.utils.BuildConstants
import org.mozilla.tv.firefox.utils.OnUrlEnteredListener
import org.mozilla.tv.firefox.utils.Settings
import org.mozilla.tv.firefox.utils.URLs
import org.mozilla.tv.firefox.utils.ViewUtils
import org.mozilla.tv.firefox.utils.publicsuffix.PublicSuffix
import org.mozilla.tv.firefox.webrender.NullSession
import org.mozilla.tv.firefox.webrender.VideoVoiceCommandMediaSession
import org.mozilla.tv.firefox.widget.InlineAutocompleteEditText

interface MediaSessionHolder {
    val videoVoiceCommandMediaSession: VideoVoiceCommandMediaSession
}

class MainActivity : LocaleAwareAppCompatActivity(), OnUrlEnteredListener, MediaSessionHolder {
    private val LOG_TAG = "MainActivity"
    private val startStopCompositeDisposable = CompositeDisposable()
    private lateinit var navigationOverlayContainer: FrameLayout

    // There should be at most one MediaSession per process, hence it's in MainActivity.
    // We crash if we init MediaSession at init time, hence lateinit.
    override lateinit var videoVoiceCommandMediaSession: VideoVoiceCommandMediaSession

    enum class Command {
        BEGIN_LOGIN
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // We override onSaveInstanceState to not save state (for handling Clear Data), so startup flow
        // goes through onCreate.
        super.onCreate(savedInstanceState)

        // Register this Activity as the current visual context BEFORE any webRenderComponents
        // access, because lazy engine initialization calls GeckoRuntime.create() which may need
        // a visual Context for WindowManager on API 31+.
        (application as FirefoxApplication).visibilityLifeCycleCallback.currentActivity = this

        PublicSuffix.init(this) // Used by Pocket Video feed & custom home tiles.
        initMediaSession()

        // The launch intent is needed to create the engines in the engine cache.
        val safeIntent = intent.toSafeIntent()
        webRenderComponents.notifyLaunchWithSafeIntent(safeIntent)

        lifecycle.addObserver(serviceLocator.engineViewCache)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        setContentView(R.layout.activity_main)
        navigationOverlayContainer = findViewById(R.id.container_navigation_overlay)

        val intentData = IntentValidator.validateOnCreate(this, safeIntent, savedInstanceState)

        val session = getOrCreateSession(intentData)

        // resetView() removed in v72+. SessionFeature handles view management.

        val screenController = serviceLocator.screenController
        screenController.setUpFragmentsForNewSession(supportFragmentManager, session)

        serviceLocator.intentLiveData.observe(this, Observer {
            it?.consume {
                if (it != null) {
                    screenController.showBrowserScreenForUrl(supportFragmentManager, it.url)
                } else {
                    if (webRenderComponents.store.state.tabs.isEmpty()) {
                        val newTab = NullSession.create()
                        webRenderComponents.store.dispatch(TabListAction.AddTabAction(newTab))
                        screenController.showBrowserScreenForCurrentSession(supportFragmentManager, newTab)
                    } else {
                        screenController.showBrowserScreenForCurrentSession(supportFragmentManager, session)
                    }
                }
                true
            }
        })

        serviceLocator.intentLiveData.value = Consumable.from(intentData)

        // Debug logging display for non public users
        // TODO: refactor out the debug variant visibility check in #1953
        val debugLog: TextView = findViewById(R.id.debugLog)
        BuildConstants.debugLogStr?.apply {
            val engineViewVersion = (this@MainActivity as Context).application.getEngineViewVersion()
            debugLog.visibility = View.VISIBLE
            debugLog.text = "$this $engineViewVersion"
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) {
        // Do not save instance state.
        //
        // onSaveInstanceState attempts to save view state, including fragments that we sometimes want destroyed. In
        // particular, this causes SettingsFragment to remain visible when it calls MainActivity#recreate in an attempt
        // to clear data.
    }

    /**
     * If a new [Session] is created, this also adds it to the SessionManager and selects it
     */
    private fun getOrCreateSession(intentData: ValidatedIntentData?): TabSessionState {
        return webRenderComponents.store.state.selectedTab
            ?: NullSession.create().also {
                val newTab = TabSessionState(
                    id = "initial-session",
                    content = mozilla.components.browser.state.state.ContentState(
                        url = intentData?.url ?: URLs.APP_URL_HOME
                    )
                )
                webRenderComponents.store.dispatch(TabListAction.AddTabAction(newTab, select = true))
                return newTab
            }
    }

    override fun onNewIntent(unsafeIntent: Intent) {
        super.onNewIntent(unsafeIntent)

        if (serviceLocator.store.state.selectedTabId == null) {
            Sentry.capture(IllegalStateException("onNewIntent is called with null selectedTab"))
            return
        }

        // We can't do anything if the intent does not contain valid data, so short.
        val safeIntent = unsafeIntent.toSafeIntent()
        val intentData = IntentValidator.validate(this, safeIntent) ?: return

        /** ScreenController operations rely on Activity.LifeCycle (i.e. FragmentTransactions)
         *  Using LiveData allows such methods to be called in the correct LifeCycle */
        serviceLocator.intentLiveData.value = Consumable.from(intentData)

        if (safeIntent.hasExtra("TURBO_MODE")) {
            val turboMode = safeIntent.getBooleanExtra("TURBO_MODE", true)
            Log.i(LOG_TAG, "Setting turboMode.isEnabled = " + turboMode)
            serviceLocator.turboMode.isEnabled = turboMode
        }
    }

    override fun applyLocale() {
        // We don't care here: all our fragments update themselves as appropriate
    }

    override fun onResume() {
        super.onResume()
        TelemetryIntegration.INSTANCE.startSession(this)

        maybeShowOnboarding()
    }

    // One onboarding activity will be shown each time any are valid. After the activity
    // is closed, MainActivity#onResume will be hit again, and the next will be displayed.
    // This ensures that only one onboarding activity is shown at any time (preventing
    // transparency issues).
    private fun maybeShowOnboarding() {
        // Skip onboarding if turbo mode is set via intent. This is used in automated perf testing.
        // See #1881 for details
        val safeIntent = intent.toSafeIntent()
        if (safeIntent.hasExtra("TURBO_MODE")) return

        val settings = Settings.getInstance(this@MainActivity)

        val onboardingIntent = when {
            settings.shouldShowTurboModeOnboarding() -> {
                Intent(this@MainActivity, OnboardingActivity::class.java)
            }
            else -> null
        }

        if (onboardingIntent != null) startActivity(onboardingIntent)
    }

    override fun onPause() {
        super.onPause()
        TelemetryIntegration.INSTANCE.stopSession(this)
    }

    override fun onStart() {
        super.onStart()

        @Suppress("DEPRECATION") // Couldn't work out a better way to do this. If you
        // think of one, please replace this
        (application as FirefoxApplication).mainActivityCommandBus
            .subscribe { command ->
                when (command) {
                    Command.BEGIN_LOGIN -> serviceLocator.fxaLoginUseCase.beginLogin(supportFragmentManager)
                    null -> { /* do nothing */ }
                }
            }
            .addTo(startStopCompositeDisposable)

        // Received tabs and polling removed with ADMIntegration in v56+.
    }

    override fun onStop() {
        super.onStop()
        LocaleManager.getInstance().resetLocaleIfChanged(applicationContext)
        TelemetryIntegration.INSTANCE.stopMainActivity()
        startStopCompositeDisposable.clear()
    }

    override fun onDestroy() {
        if (webRenderComponents.store.state.tabs.isNotEmpty()) {
            /**
             * This is to clear the previously assigned WebView instance from EngineView (which
             * uses ActivityContext) when it's destroyed via [EngineViewCache.onDestroy].
             *
             * webView instance is stored in the session, which means they'd stick around for the
             * life-time of the app. So we would need to manually deallocate the webView whenever
             * it's destroyed.
             *
             * Since [EngineSession.webView] is not nullable, we let [EngineSession.webView]
             * assign to a new bogus WebView instance (with ApplicationContext). This allows previously
             * assigned webView to be garbage collected and the newly assigned bogus webView to be replaced
             * in [MainActivity.onCreate]
             *
             * See [EngineSession.resetView] for additional context
             */
            // resetView() removed in v72+. SessionFeature handles view management.
        }
        super.onDestroy()
    }

    override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
        return if (name == EngineView::class.java.name) {
            context.serviceLocator.engineViewCache.getEngineView(context, attrs) {
                setupForApp()
            }
        } else super.onCreateView(parent, name, context, attrs)
    }

    override fun onBackPressed() {
        Log.d("MainActivity", "onBackPressed called")
        val handled = serviceLocator.screenController.handleBack(supportFragmentManager)
        Log.d("MainActivity", "handleBack returned: $handled")
        if (handled) return

        // If you're here that means there's nothing else in the fragment backstack; therefore, clear session
        Log.d("MainActivity", "Exiting app - removing tab and calling super.onBackPressed()")
        webRenderComponents.store.state.selectedTabId?.let {
            webRenderComponents.store.dispatch(TabListAction.RemoveTabAction(it))
        }

        super.onBackPressed()
    }

    private fun initMediaSession() {
        videoVoiceCommandMediaSession = VideoVoiceCommandMediaSession(this)
        lifecycle.addObserver(videoVoiceCommandMediaSession)
    }

    override fun onNonTextInputUrlEntered(urlStr: String) {
        ViewUtils.hideKeyboard(navigationOverlayContainer)
        serviceLocator.screenController.onUrlEnteredInner(this, supportFragmentManager, urlStr, false,
                null, null)
    }

    override fun onTextInputUrlEntered(
        urlStr: String,
        autocompleteResult: InlineAutocompleteEditText.AutocompleteResult?,
        inputLocation: UrlTextInputLocation?
    ) {
        ViewUtils.hideKeyboard(navigationOverlayContainer)
        // It'd be much cleaner/safer to do this with a kotlin callback.
        serviceLocator.screenController.onUrlEnteredInner(this, supportFragmentManager, urlStr, true,
                autocompleteResult, inputLocation)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Back presses are all handled through onBackPressed.
        //
        // Note: on device, back presses emit one KEYCODE_BACK. On emulator, they
        // emit one KEYCODE_BACK **AND** one KEYCODE_DEL. We short on both to make
        // code paths consistent between the two.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN) onBackPressed()
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_DEL) return true

        val fragmentManager = supportFragmentManager

        TelemetryIntegration.INSTANCE.saveRemoteControlInformation(applicationContext, event)

        return videoVoiceCommandMediaSession.dispatchKeyEvent(event) ||
                serviceLocator.screenController.dispatchKeyEvent(event, fragmentManager) ||
                super.dispatchKeyEvent(event)
    }

}
