/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.architecture

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import org.atmofox.tv.R
import org.atmofox.tv.hint.HintContentFactory
import org.atmofox.tv.navigationoverlay.ChannelTitles
import org.atmofox.tv.navigationoverlay.NavigationOverlayViewModel
import org.atmofox.tv.navigationoverlay.ToolbarViewModel
import org.atmofox.tv.settings.SettingsViewModel
import org.atmofox.tv.utils.ServiceLocator
import org.atmofox.tv.webrender.WebRenderViewModel

/**
 * Used by [ViewModelProviders] to instantiate [ViewModel]s with constructor arguments.
 *
 * This should be used through [FirefoxViewModelProviders.of].
 * Example usage:
 * ```kotlin
 * val myViewModel = FirefoxViewModelProviders.of(this).get(ExampleViewModel::class.java)
 * ```
 */
class ViewModelFactory(
    private val serviceLocator: ServiceLocator,
    private val app: Application
) : ViewModelProvider.Factory {

    private val resources = app.resources
    private val hintContentFactory = HintContentFactory(resources)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            ToolbarViewModel::class.java -> ToolbarViewModel(
                sessionRepo = serviceLocator.sessionRepo,
                pinnedTileRepo = serviceLocator.pinnedTileRepo
            ) as T

            SettingsViewModel::class.java -> SettingsViewModel(
                serviceLocator.settingsRepo,
                serviceLocator.sessionRepo
            ) as T

            NavigationOverlayViewModel::class.java -> NavigationOverlayViewModel(
                serviceLocator.screenController,
                ChannelTitles(
                    pinned = app.getString(R.string.pinned_tile_channel_title),
                    newsAndPolitics = resources.getString(R.string.news_channel_title),
                    sports = resources.getString(R.string.sports_channel_title),
                    music = resources.getString(R.string.music_channel_title),
                    food = resources.getString(R.string.food_channel_title)
                ),
                serviceLocator.channelRepo,
                ToolbarViewModel(
                        sessionRepo = serviceLocator.sessionRepo,
                        pinnedTileRepo = serviceLocator.pinnedTileRepo
                ),
                serviceLocator.fxaRepo,
                serviceLocator.fxaLoginUseCase
            ) as T


            WebRenderViewModel::class.java -> WebRenderViewModel(
                serviceLocator.screenController,
                serviceLocator.fxaLoginUseCase
            ) as T

        // This class needs to either return a ViewModel or throw, so we have no good way of silently handling
        // failures in production. However a failure could only occur if code requests a VM that we have not added
        // to this factory, so any problems should be caught in dev.
            else -> throw IllegalArgumentException(
                "A class was passed to ViewModelFactory#create that it does not " +
                    "know how to handle\nClass name: ${modelClass.simpleName}"
            )
        }
    }
}
