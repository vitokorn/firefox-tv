/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.channels

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.atmofox.tv.R
import org.atmofox.tv.channels.ImageSetStrategy
import org.atmofox.tv.channels.pinnedtile.PinnedTileImageUtilWrapper
import org.atmofox.tv.channels.pinnedtile.PinnedTileRepo
import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner
import org.atmofox.tv.utils.FormattedDomainWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(FirefoxRobolectricTestRunner::class)
class ChannelRepoTest {

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private fun createRepo(): ChannelRepo {
        val pinnedTileRepo = mockk<PinnedTileRepo>(relaxed = true)
        every { pinnedTileRepo.pinnedTiles } returns MutableStateFlow(linkedMapOf())

        return ChannelRepo(
            appContext() as Application,
            PinnedTileImageUtilWrapper(appContext() as Application),
            FormattedDomainWrapper(appContext() as Application),
            pinnedTileRepo
        )
    }

    // TODO: Update test when getTitle method is implemented
    // @Test
    // fun `getTitle returns correct title for each category`() {
    //     val repo = createRepo()
    //
    //     assertEquals("Firefox - Main", repo.getTitle(TileSource.BUNDLED))
    //     assertEquals("News", repo.getTitle(TileSource.NEWS))
    //     assertEquals("Sports", repo.getTitle(TileSource.SPORTS))
    //     assertEquals("Music", repo.getTitle(TileSource.MUSIC))
    // }

    @Test
    fun `removeChannelContent adds news tile to blacklist`() {
        val repo = createRepo()
        val tile = ChannelTile(
            url = "https://news.example.com",
            title = "News",
            subtitle = null,
            setImage = ImageSetStrategy.ById(R.drawable.ic_home),
            tileSource = TileSource.NEWS,
            id = "news-123"
        )

        repo.removeChannelContent(tile)

        val prefs = appContext().getSharedPreferences("ChannelRepo", Context.MODE_PRIVATE)
        val blacklist = prefs.getStringSet("blacklist_news", emptySet())
        assertTrue(blacklist!!.contains("news-123"))
    }

    @Test
    fun `removeChannelContent adds sports tile to blacklist`() {
        val repo = createRepo()
        val tile = ChannelTile(
            url = "https://sports.example.com",
            title = "Sports",
            subtitle = null,
            setImage = ImageSetStrategy.ById(R.drawable.ic_home),
            tileSource = TileSource.SPORTS,
            id = "sports-456"
        )

        repo.removeChannelContent(tile)

        val prefs = appContext().getSharedPreferences("ChannelRepo", Context.MODE_PRIVATE)
        val blacklist = prefs.getStringSet("blacklist_sports", emptySet())
        assertTrue(blacklist!!.contains("sports-456"))
    }

    @Test
    fun `removeChannelContent adds music tile to blacklist`() {
        val repo = createRepo()
        val tile = ChannelTile(
            url = "https://music.example.com",
            title = "Music",
            subtitle = null,
            setImage = ImageSetStrategy.ById(R.drawable.ic_home),
            tileSource = TileSource.MUSIC,
            id = "music-789"
        )

        repo.removeChannelContent(tile)

        val prefs = appContext().getSharedPreferences("ChannelRepo", Context.MODE_PRIVATE)
        val blacklist = prefs.getStringSet("blacklist_music", emptySet())
        assertTrue(blacklist!!.contains("music-789"))
    }

    // TODO: Update test when isUrlInBlacklist method is implemented
    // @Test
    // fun `isUrlInBlacklist returns true for blacklisted url`() {
    //     val repo = createRepo()
    //     val tile = ChannelTile(
    //         url = "https://news.example.com",
    //         title = "News",
    //         subtitle = null,
    //         setImage = ImageSetStrategy.ById(R.drawable.ic_home),
    //         tileSource = TileSource.NEWS,
    //         id = "news-blacklist-test"
    //     )
    //
    //     repo.removeChannelContent(tile)
    //     assertTrue(repo.isUrlInBlacklist("https://news.example.com", TileSource.NEWS))
    // }

    // TODO: Update test when isUrlInBlacklist method is implemented
    // @Test
    // fun `isUrlInBlacklist returns false for non blacklisted url`() {
    //     val repo = createRepo()
    //     assertFalse(repo.isUrlInBlacklist("https://not-listed.com", TileSource.NEWS))
    // }
}
