/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.webrender

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Bundle
import org.atmofox.tv.FirefoxApplication

/**
 * This ActivityLifecycleCallbacks implementations tracks if there is at least one activity in the
 * STARTED state (meaning some part of our application is visible).
 * Based on this information the current task can be removed if the app is not visible.
 */
class VisibilityLifeCycleCallback(context: Context) : Application.ActivityLifecycleCallbacks {
    companion object {
        /**
         * If all activities of this app are in the background then finish and remove all tasks. After
         * that the app won't show up in "recent apps" anymore.
         */
        @JvmStatic
        fun finishAndRemoveTaskIfInBackground(context: Context) {
            (context.applicationContext as FirefoxApplication)
                .visibilityLifeCycleCallback
                .finishAndRemoveTaskIfInBackground()
        }
    }

    private val context: Context = context

    /**
     * Activities are not stopped/started in an ordered way. So we are using
     */
    private var activitiesInStartedState = 0

    var currentActivity: Activity? = null

    private fun finishAndRemoveTaskIfInBackground() {
        if (activitiesInStartedState == 0) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return

            for (task in activityManager.appTasks) {
                task.finishAndRemoveTask()
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {
        activitiesInStartedState++
    }

    override fun onActivityStopped(activity: Activity) {
        activitiesInStartedState--
    }

    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        currentActivity = activity
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}
