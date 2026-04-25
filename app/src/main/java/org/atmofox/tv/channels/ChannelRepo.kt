/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.channels

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.atmofox.tv.channels.content.ChannelContent
import org.atmofox.tv.channels.content.getMusicChannels
import org.atmofox.tv.channels.content.getNewsChannels
import org.atmofox.tv.channels.content.getSportsChannels
import org.atmofox.tv.channels.pinnedtile.PinnedTileImageUtilWrapper
import org.atmofox.tv.channels.pinnedtile.PinnedTileRepo
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.utils.FormattedDomainWrapper
import java.util.Collections

private const val PREF_CHANNEL_REPO = "ChannelRepo"

// BlackList Ids
private const val BUNDLED_PINNED_SITES_ID_BLACKLIST = "blacklist_pinned_tiles"
private const val BUNDLED_NEWS_ID_BLACKLIST = "blacklist_news"
private const val BUNDLED_SPORTS_ID_BLACKLIST = "blacklist_sports"
private const val BUNDLED_MUSIC_ID_BLACKLIST = "blacklist_music"

/**
 * ChannelRepo abstracts app logic that requires exposures to other repos (e.g. removing a pinned
 * tile channel would require a reference to [PinnedTileRepo].
 *
 * [TileSource] is used to determine which Repo is responsible to handle requested operations
 */
class ChannelRepo(
    application: Application,
    imageUtilityWrapper: PinnedTileImageUtilWrapper,
    formattedDomainWrapper: FormattedDomainWrapper,
    private val pinnedTileRepo: PinnedTileRepo
) {
    private val _sharedPreferences by lazy {
        application.getSharedPreferences(PREF_CHANNEL_REPO, Context.MODE_PRIVATE)
    }

    private val pinnedTiles = pinnedTileRepo.pinnedTiles
        // This takes place off of the main thread because PinnedTile.toChannelTile needs
        // to perform file access, and blocks to do so
        .map { tiles -> tiles.values.toList().map { it.toChannelTile(imageUtilityWrapper, formattedDomainWrapper) } }
        .flowOn(Dispatchers.IO)
    private val blacklistedPinnedIds = MutableStateFlow(emptySet<String>())
    private val bundledNewsTiles = MutableStateFlow(ChannelContent.getNewsChannels())
    private val blacklistedNewsIds = MutableStateFlow(emptySet<String>())

    private val bundledSportsTiles = MutableStateFlow(ChannelContent.getSportsChannels())
    private val blacklistedSportsIds = MutableStateFlow(emptySet<String>())

    private val bundledMusicTiles = MutableStateFlow(ChannelContent.getMusicChannels())
    private val blacklistedMusicIds = MutableStateFlow(emptySet<String>())

    init {
        GlobalScope.launch(Dispatchers.IO) {
            blacklistedPinnedIds.value = loadBlackList(TileSource.BUNDLED)
            blacklistedNewsIds.value = loadBlackList(TileSource.NEWS)
            blacklistedSportsIds.value = loadBlackList(TileSource.SPORTS)
            blacklistedMusicIds.value = loadBlackList(TileSource.MUSIC)
        }
    }

    val pinnedTilesFlow: StateFlow<List<ChannelTile>> =
        pinnedTiles.filterNotBlacklisted(blacklistedPinnedIds)
            .stateIn(GlobalScope, SharingStarted.Lazily, emptyList())

    val newsTilesFlow: StateFlow<List<ChannelTile>> =
        bundledNewsTiles.filterNotBlacklisted(blacklistedNewsIds)
            .stateIn(GlobalScope, SharingStarted.Lazily, emptyList())

    val sportsTilesFlow: StateFlow<List<ChannelTile>> =
        bundledSportsTiles.filterNotBlacklisted(blacklistedSportsIds)
            .stateIn(GlobalScope, SharingStarted.Lazily, emptyList())

    val musicTilesFlow: StateFlow<List<ChannelTile>> =
        bundledMusicTiles.filterNotBlacklisted(blacklistedMusicIds)
            .stateIn(GlobalScope, SharingStarted.Lazily, emptyList())

    fun removeChannelContent(tileData: ChannelTile) {
        when (tileData.tileSource) {
            TileSource.CUSTOM -> {
                TelemetryIntegration.INSTANCE.homeTileRemovedEvent(tileData)
                pinnedTileRepo.removePinnedTile(tileData.url)
            }
            TileSource.BUNDLED -> {
                TelemetryIntegration.INSTANCE.homeTileRemovedEvent(tileData) // TODO: verify if we need news, sports and music tiles tracked
                addBundleTileToBlackList(tileData.tileSource, tileData.id)
                pinnedTileRepo.removePinnedTile(tileData.url)
            }
            TileSource.NEWS, TileSource.SPORTS, TileSource.MUSIC -> {
                addBundleTileToBlackList(tileData.tileSource, tileData.id)
            }
        }
    }

    /**
     * Used to handle removing bundle tiles by adding to its [BundleType] blacklist in Sha¬redPreferences
     */
    private fun addBundleTileToBlackList(source: TileSource, id: String) {
        val blackList = loadBlackList(source).toMutableSet()
        blackList.add(id)

        when (source) {
            TileSource.BUNDLED -> blacklistedPinnedIds.value = blackList
            TileSource.NEWS -> blacklistedNewsIds.value = blackList
            TileSource.SPORTS -> blacklistedSportsIds.value = blackList
            TileSource.MUSIC -> blacklistedMusicIds.value = blackList
            else -> Unit
        }

        saveBlackList(source, blackList)
    }

    private fun loadBlackList(source: TileSource): Set<String> {
        val sharedPrefKey = when (source) {
            TileSource.BUNDLED -> BUNDLED_PINNED_SITES_ID_BLACKLIST
            TileSource.NEWS -> BUNDLED_NEWS_ID_BLACKLIST
            TileSource.SPORTS -> BUNDLED_SPORTS_ID_BLACKLIST
            TileSource.MUSIC -> BUNDLED_MUSIC_ID_BLACKLIST
            else -> throw NotImplementedError("other types shouldn't be able remove tiles")
        }

        return _sharedPreferences.getStringSet(sharedPrefKey, Collections.emptySet())!!
    }

    private fun saveBlackList(source: TileSource, blackList: Set<String>) {
        val sharedPrefKey = when (source) {
            TileSource.BUNDLED -> BUNDLED_PINNED_SITES_ID_BLACKLIST
            TileSource.NEWS -> BUNDLED_NEWS_ID_BLACKLIST
            TileSource.SPORTS -> BUNDLED_SPORTS_ID_BLACKLIST
            TileSource.MUSIC -> BUNDLED_MUSIC_ID_BLACKLIST
            else -> throw NotImplementedError("other types shouldn't be able remove tiles")
        }

        _sharedPreferences.edit().putStringSet(sharedPrefKey, blackList.toSet()).apply()
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
fun Flow<List<ChannelTile>>.filterNotBlacklisted(
    blacklistIds: StateFlow<Set<String>>
): Flow<List<ChannelTile>> {
    return combine(this, blacklistIds) { tiles, blacklistIds -> tiles.filter { !blacklistIds.contains(it.id) } }
}
