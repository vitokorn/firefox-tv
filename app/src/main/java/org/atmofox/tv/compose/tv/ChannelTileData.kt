/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.tv

/**
 * Ported from firefox-tv to Compose
 * Data model for a channel tile
 */
enum class TileSource { BUNDLED, CUSTOM, NEWS, SPORTS, MUSIC }

/**
 * Backing data for a tile in a channel
 * Ported from ChannelTile.kt, adapted for Compose
 */
data class ChannelTileData(
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val imageRes: Int? = null,  // For bundled resources
    val imagePath: String? = null,  // For URLs or local paths
    val tileSource: TileSource,
    val id: String
) {
    /**
     * Generate localized modal title based on tile source
     */
    fun generateRemoveTileTitleStr(context: android.content.Context): String {
        return when (tileSource) {
            TileSource.BUNDLED, TileSource.CUSTOM ->
                context.resources.getString(
                    org.atmofox.tv.R.string.pinned_tiles_channel_remove_title,
                    title
                )
            TileSource.NEWS ->
                context.resources.getString(
                    org.atmofox.tv.R.string.news_channel_remove_title,
                    title
                )
            TileSource.SPORTS ->
                context.resources.getString(
                    org.atmofox.tv.R.string.sports_channel_remove_title,
                    title
                )
            TileSource.MUSIC ->
                context.resources.getString(
                    org.atmofox.tv.R.string.music_channel_remove_title,
                    title
                )
        }
    }
}

/**
 * Backing data for a channel as a whole
 * Ported from ChannelDetails.kt
 */
data class ChannelDetailsData(
    val title: String,
    val subtitle: String? = null,
    val tileList: List<ChannelTileData>,
    val isEnabled: Boolean = true
)
