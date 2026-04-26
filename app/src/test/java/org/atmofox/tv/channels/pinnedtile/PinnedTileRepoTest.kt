/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.channels.pinnedtile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.LinkedHashMap
import java.util.UUID
import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner

@RunWith(FirefoxRobolectricTestRunner::class)
class PinnedTileRepoTest {

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `loadTilesCache places featured bundled tiles first`() {
        val repo = PinnedTileRepo(appContext())
        val featured = BundledPinnedTile("https://youtube.com", "YouTube", "youtube.png", "youtube")
        val unfeatured = BundledPinnedTile("https://example.com", "Example", "example.png", "example")

        val bundled = linkedMapOf("https://example.com" to unfeatured, "https://youtube.com" to featured)
        val custom = LinkedHashMap<String, CustomPinnedTile>()

        val result = repo.loadTilesCache(bundled, custom)

        assertEquals(2, result.size)
        assertEquals("youtube", (result.values.first() as BundledPinnedTile).id)
        assertEquals("example", (result.values.elementAt(1) as BundledPinnedTile).id)
    }

    @Test
    fun `loadTilesCache places custom tiles between featured and unfeatured bundled`() {
        val repo = PinnedTileRepo(appContext())
        val featured = BundledPinnedTile("https://youtube.com", "YouTube", "youtube.png", "youtube")
        val unfeatured = BundledPinnedTile("https://example.com", "Example", "example.png", "example")
        val custom = CustomPinnedTile("https://custom.com", "custom", UUID.randomUUID())

        val bundled = linkedMapOf("https://example.com" to unfeatured, "https://youtube.com" to featured)
        val customMap = linkedMapOf("https://custom.com" to custom)

        val result = repo.loadTilesCache(bundled, customMap)

        assertEquals(3, result.size)
        assertEquals("youtube", (result.values.elementAt(0) as BundledPinnedTile).id)
        assertEquals("custom", (result.values.elementAt(1) as CustomPinnedTile).title)
        assertEquals("example", (result.values.elementAt(2) as BundledPinnedTile).id)
    }

    @Test
    fun `addPinnedTile adds tile to repo`() {
        val repo = PinnedTileRepo(appContext())

        repo.addPinnedTile("https://newsite.com", null)

        assertTrue(repo.pinnedTiles.value.containsKey("https://newsite.com"))
        assertFalse(repo.isEmpty.value)
    }

    @Test
    fun `removePinnedTile removes custom tile and returns id`() {
        val repo = PinnedTileRepo(appContext())
        val id = UUID.randomUUID()
        repo.addPinnedTile("https://remove.com", null)
        // Replace the auto-generated tile with one having a known UUID so we can assert the returned id
        val pinnedTilesField = PinnedTileRepo::class.java.getDeclaredField("_pinnedTiles")
        pinnedTilesField.isAccessible = true
        val pinnedTilesFlow = pinnedTilesField.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<*>
        val mutableMap = java.util.LinkedHashMap<String, PinnedTile>()
        mutableMap["https://remove.com"] = CustomPinnedTile("https://remove.com", "custom", id)
        @Suppress("UNCHECKED_CAST")
        (pinnedTilesFlow as kotlinx.coroutines.flow.MutableStateFlow<java.util.LinkedHashMap<String, PinnedTile>>).value = mutableMap

        val isEmptyField = PinnedTileRepo::class.java.getDeclaredField("_isEmpty")
        isEmptyField.isAccessible = true
        val isEmptyFlow = isEmptyField.get(repo) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
        isEmptyFlow.value = false

        val removedId = repo.removePinnedTile("https://remove.com")

        assertNotNull(removedId)
        assertEquals(id.toString(), removedId)
        assertFalse(repo.pinnedTiles.value.containsKey("https://remove.com"))
    }

    @Test
    fun `removePinnedTile returns null for unknown url`() {
        val repo = PinnedTileRepo(appContext())

        val removedId = repo.removePinnedTile("https://unknown.com")

        assertNull(removedId)
    }
}
