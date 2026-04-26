/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv

import android.app.Activity
import android.content.Context
import android.os.StrictMode
import android.os.Trace
import androidx.annotation.VisibleForTesting
import android.webkit.WebSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mozilla.appservices.Megazord
import mozilla.components.concept.engine.utils.EngineVersion
import mozilla.components.lib.fetch.okhttp.OkHttpClient
import coil.Coil
import coil.ImageLoader
import coil.memory.MemoryCache
import androidx.work.Configuration as WorkManagerConfiguration
import androidx.work.WorkManager
import mozilla.components.service.glean.Glean
import mozilla.components.service.glean.config.Configuration
import mozilla.components.service.glean.net.ConceptFetchHttpUploader
import mozilla.telemetry.glean.BuildInfo
import java.util.Calendar
import mozilla.components.lib.fetch.httpurlconnection.HttpURLConnectionClient
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.ktx.android.os.resetAfter
import mozilla.components.support.rusthttp.RustHttpConfig
import org.atmofox.tv.components.locale.LocaleAwareApplication
import org.atmofox.tv.ext.webRenderComponents
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.atmofox.tv.telemetry.SentryIntegration
import org.atmofox.tv.webrender.VisibilityLifeCycleCallback
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.utils.BuildConstants
import org.atmofox.tv.utils.OkHttpWrapper
import org.atmofox.tv.utils.ServiceLocator
import org.atmofox.tv.webrender.WebRenderComponents
import java.util.UUID

private const val DEFAULT_LOGTAG = "FFTV"

open class FirefoxApplication : LocaleAwareApplication() {
    lateinit var visibilityLifeCycleCallback: VisibilityLifeCycleCallback
        private set

    @VisibleForTesting
    protected open fun getSystemUserAgent(): String {
        // For gecko builds, use a modern Firefox User-Agent instead of system WebView UA
        // to avoid being blocked by sites that check for old browser versions
        return if (BuildConstants.isGeckoBuild) {
            // Modern Firefox 128.x User-Agent
            "Mozilla/5.0 (Android ${android.os.Build.VERSION.RELEASE}; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
        } else {
            WebSettings.getDefaultUserAgent(this)
        }
    }

    // See the TestFirefoxApplication impl for why this method exists.
    open fun getEngineViewVersion(): EngineVersion = webRenderComponents.engine.version

    /**
     * Reference to components needed by the application.
     *
     * We create this instance lazily because at the time FirefoxApplication gets constructed it is
     * not a valid Context object just yet (The Android system needs to call attachBaseContext()
     * first). Therefore we delay the creation so that the components can access and use the
     * application context at the time they get created.
     */
    val components by lazy { WebRenderComponents(this, getSystemUserAgent()) }
    lateinit var serviceLocator: ServiceLocator
    @Volatile
    private var deferredStartupInitialized = false

    private inline fun <T> traceStartupSection(name: String, block: () -> T): T {
        Trace.beginSection(name)
        return try {
            block()
        } finally {
            Trace.endSection()
        }
    }

    override fun onCreate() {
        super.onCreate()

        enableAndroidComponentsLogging() // In theory, the Gecko process may use this logger so init for all processes.

        // If this is not the main process then do not continue with the initialization here. Everything that
        // follows only needs to be done in our app's main process and should not be done in other processes like
        // a GeckoView child process or the crash handling process. Most importantly we never want to end up in a
        // situation where we create a GeckoRuntime from the Gecko child process
        applicationContext.runOnlyInMainProcess {
            traceStartupSection("fftv.app.create_service_locator") {
                serviceLocator = createServiceLocator()
            }

            // Enable crash reporting. Don't add anything above here because if it crashes, we won't know.
            traceStartupSection("fftv.app.init_sentry") {
                SentryIntegration.init(this, serviceLocator.settingsRepo)
            }

            traceStartupSection("fftv.app.init_rust_dependencies") {
                initRustDependencies()
            }

            enableStrictMode()

            traceStartupSection("fftv.app.register_activity_lifecycle") {
                visibilityLifeCycleCallback = VisibilityLifeCycleCallback(this).also {
                    registerActivityLifecycleCallbacks(it)
                }
            }

            // Configure Coil with reduced memory cache to limit footprint on low-RAM TV devices
            val coilImageLoader = ImageLoader.Builder(this)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.1)
                        .build()
                }
                .build()
            Coil.setImageLoader(coilImageLoader)
        }
    }

    @Synchronized
    fun maybeInitDeferredStartup() {
        if (deferredStartupInitialized) {
            return
        }

        traceStartupSection("fftv.app.deferred_init_workmanager") {
            try {
                WorkManager.initialize(this, WorkManagerConfiguration.Builder().build())
            } catch (_: IllegalStateException) {
            }
        }

        traceStartupSection("fftv.app.init_telemetry") {
            TelemetryIntegration.INSTANCE.init(this)
        }
        traceStartupSection("fftv.app.init_glean") {
            initGlean()
        }
        traceStartupSection("fftv.app.init_fretboard") {
            initFretboard()
        }

        deferredStartupInitialized = true
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected open fun initRustDependencies() {
        Megazord.init()
        RustHttpConfig.setClient(lazy { OkHttpClient(OkHttpWrapper.client, this) })
    }

    private fun initFretboard() {
        with(serviceLocator.fretboardProvider) {
            loadExperiments()
            updateExperiments()
        }
    }

    // This method is used to call Glean.setUploadEnabled. During the tests, this is
    // overridden to disable ping upload.
    @VisibleForTesting
    protected open fun setGleanUpload() {
        GlobalScope.launch {
            serviceLocator.settingsRepo.dataCollectionEnabled.collect { collectionEnabled ->
                // This needs to be called before Glean.initialize, or we risk 1) not
                // sending startup data, or 2) sending even when the user has toggled
                // off data collection
                Glean.setUploadEnabled(collectionEnabled)
                if (collectionEnabled) {
                    // LegacyIds.clientId.set(UUID.fromString(TelemetryIntegration.INSTANCE.clientId))
                }
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected open fun initGlean() {
        setGleanUpload()
        // LegacyIds.clientId.set(UUID.fromString(TelemetryIntegration.INSTANCE.clientId))
        Glean.initialize(
            applicationContext,
            uploadEnabled = true,
            configuration = Configuration(
                httpClient = ConceptFetchHttpUploader(lazy { HttpURLConnectionClient() }),
                channel = BuildConfig.BUILD_TYPE
            ),
            buildInfo = BuildInfo(
                versionCode = BuildConfig.VERSION_CODE.toString(),
                versionName = BuildConfig.VERSION_NAME,
                buildDate = Calendar.getInstance()
            )
        )
    }

    // ServiceLocator needs to be created in onCreate in order to accept Application
    // as an argument. Because of this, if we override `val serviceLocator` but
    // accidentally called `super.onCreate`, it would overwrite our test
    // ServiceLocator. To prevent this land mine, we override this method instead
    open fun createServiceLocator() = ServiceLocator(this)

    protected open fun enableStrictMode() {
        // Android/WebView sometimes commit strict mode violations, see e.g.
        // https://github.com/mozilla-mobile/focus-android/issues/660
        if (BuildConstants.isReleaseBuild) {
            return
        }

        val threadPolicyBuilder = StrictMode.ThreadPolicy.Builder().detectAll()
        val vmPolicyBuilder = StrictMode.VmPolicy.Builder().detectAll()

        threadPolicyBuilder
            .penaltyLog()
        vmPolicyBuilder.penaltyLog()

        StrictMode.setThreadPolicy(threadPolicyBuilder.build())
        StrictMode.setVmPolicy(vmPolicyBuilder.build())
    }

    override fun getSystemService(name: String): Any? {
        if (Context.WINDOW_SERVICE == name) {
            val activity = visibilityLifeCycleCallback.currentActivity
            if (activity != null && !activity.isDestroyed) {
                return activity.getSystemService(name)
            }
        }
        return super.getSystemService(name)
    }

    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        OkHttpWrapper.onLowMemory()
        // sessionManager.onLowMemory() removed in v72+.
        Coil.imageLoader(this).memoryCache?.clear()
    }

    @Deprecated("Avoid using this bus whenever possible. Only use it if the alternatives are even worse")
    val mainActivityCommandBus = MutableSharedFlow<MainActivity.Command>(extraBufferCapacity = 1)
}

private fun enableAndroidComponentsLogging() {
    Log.addSink(AndroidLogSink(defaultTag = DEFAULT_LOGTAG))
}
