/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.mozilla.tv.firefox.R
import org.mozilla.tv.firefox.architecture.KillswitchLayout

// todo: kdoc
class DefaultChannelFactory(
    private val loadUrl: (String) -> Unit,
    val onTileFocused: (() -> Unit)
) {

    fun createChannel(
        parent: ViewGroup,
        id: Int? = null,
        channelConfig: ChannelConfig
    ): DefaultChannel {
        val context = parent.context
        val channelAdapter = DefaultChannelAdapter(context, loadUrl, onTileFocused, channelConfig)

        val containerView = LayoutInflater.from(context).inflate(R.layout.default_channel, parent, false) as KillswitchLayout
        val channelTileContainer: RecyclerView = containerView.findViewById(R.id.channelTileContainer)

        channelTileContainer.apply {
            val channelLayoutManager = ChannelLayoutManager(context)
            layoutManager = channelLayoutManager

            adapter = channelAdapter

            // This blocks Android handle request ViewGroup descendant focus allowing us to handle
            // default childFocus logic; see more in [ChannelLayoutManager.requestDefaultFocus]
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    channelLayoutManager.requestDefaultFocus()
                }
            }
        }
        containerView.setRequirements(
            isAllowedByCurrentExperiment = channelConfig.isEnabledInCurrentExperiment,
            allowedInLocales = channelConfig.enabledInLocales
        )
        if (id != null) channelTileContainer.id = id

        return DefaultChannel(
                channelContainer = containerView,
                adapter = channelAdapter
        )
    }
}
