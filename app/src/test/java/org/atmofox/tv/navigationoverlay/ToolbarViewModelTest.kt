/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.navigationoverlay

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.atmofox.tv.channels.pinnedtile.CustomPinnedTile
import org.atmofox.tv.channels.pinnedtile.PinnedTileRepo
import org.atmofox.tv.helpers.MainDispatcherRule
import org.atmofox.tv.session.SessionRepo
import org.atmofox.tv.webrender.EngineViewCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.LinkedHashMap
import java.util.UUID

class ToolbarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionState = MutableStateFlow(
        SessionRepo.State(
            backEnabled = true,
            forwardEnabled = true,
            desktopModeActive = false,
            turboModeActive = true,
            currentUrl = "https://example.com",
            loading = false
        )
    )

    private val pinnedTiles = MutableStateFlow(LinkedHashMap<String, org.atmofox.tv.channels.pinnedtile.PinnedTile>())

    private val sessionRepo: SessionRepo = mock(SessionRepo::class.java).apply {
        `when`(state).thenReturn(sessionState)
        `when`(currentState()).thenAnswer { sessionState.value }
    }

    private val pinnedTileRepo: PinnedTileRepo = mock(PinnedTileRepo::class.java).apply {
        `when`(pinnedTiles).thenReturn(this@ToolbarViewModelTest.pinnedTiles)
    }

    private val engineViewCache = mockk<EngineViewCache>(relaxed = true)

    private fun buildViewModel(): ToolbarViewModel = ToolbarViewModel(
        sessionRepo = sessionRepo,
        pinnedTileRepo = pinnedTileRepo,
        engineViewCache = engineViewCache
    )

    private fun <T> collectState(flow: kotlinx.coroutines.flow.StateFlow<T>): Pair<MutableList<T>, kotlinx.coroutines.Job> {
        val values = mutableListOf<T>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = flow.onEach { values.add(it) }.launchIn(scope)
        return values to job
    }

    private fun collectEvents(flow: kotlinx.coroutines.flow.SharedFlow<mozilla.components.support.base.observer.Consumable<ToolbarViewModel.Action>>): Pair<MutableList<ToolbarViewModel.Action>, kotlinx.coroutines.Job> {
        val events = mutableListOf<ToolbarViewModel.Action>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = flow.onEach { c -> c.consume { events.add(it); true } }.launchIn(scope)
        return events to job
    }

    @Test
    fun `state maps session repo state to UI state`() {
        val vm = buildViewModel()
        val (states, job) = collectState(vm.state)

        val state = states.last()
        assertEquals("https://example.com", state.urlBarText)
        assertTrue(state.backEnabled)
        assertTrue(state.forwardEnabled)
        assertTrue(state.turboChecked)
        assertFalse(state.desktopModeChecked)
        assertTrue(state.refreshEnabled)
        assertTrue(state.pinEnabled)
        assertFalse(state.pinChecked)
        job.cancel()
    }

    @Test
    fun `pinEnabled and refreshEnabled are false on homepage`() {
        sessionState.value = SessionRepo.State(
            backEnabled = false,
            forwardEnabled = false,
            desktopModeActive = false,
            turboModeActive = false,
            currentUrl = "data:text/html,<html></html>",
            loading = false
        )

        val vm = buildViewModel()
        val (states, job) = collectState(vm.state)

        val state = states.last()
        assertFalse(state.pinEnabled)
        assertFalse(state.refreshEnabled)
        assertFalse(state.desktopModeEnabled)
        job.cancel()
    }

    @Test
    fun `pinChecked is true when URL is pinned`() {
        pinnedTiles.value = LinkedHashMap<String, org.atmofox.tv.channels.pinnedtile.PinnedTile>().apply {
            put("https://example.com", CustomPinnedTile("https://example.com", "Example", UUID.randomUUID()))
        }

        val vm = buildViewModel()
        val (states, job) = collectState(vm.state)

        assertTrue(states.last().pinChecked)
        job.cancel()
    }

    @Test
    fun `backButtonClicked calls attemptBack and hides overlay`() {
        val vm = buildViewModel()
        val (events, job) = collectEvents(vm.events)

        vm.backButtonClicked()

        verify(sessionRepo).attemptBack(forceYouTubeExit = true)
        assertEquals(1, events.size)
        assertTrue(events[0] is ToolbarViewModel.Action.SetOverlayVisible)
        assertFalse((events[0] as ToolbarViewModel.Action.SetOverlayVisible).visible)
        job.cancel()
    }

    @Test
    fun `forwardButtonClicked calls goForward and hides overlay`() {
        val vm = buildViewModel()
        val (events, job) = collectEvents(vm.events)

        vm.forwardButtonClicked()

        verify(sessionRepo).goForward()
        assertEquals(1, events.size)
        assertTrue(events[0] is ToolbarViewModel.Action.SetOverlayVisible)
        job.cancel()
    }

    @Test
    fun `reloadButtonClicked calls reload and hides overlay`() {
        val vm = buildViewModel()
        val (events, job) = collectEvents(vm.events)

        vm.reloadButtonClicked()

        verify(sessionRepo).reload()
        verify(sessionRepo).pushCurrentValue()
        assertEquals(1, events.size)
        assertTrue(events[0] is ToolbarViewModel.Action.SetOverlayVisible)
        job.cancel()
    }

    @Test
    fun `pinButtonClicked unpins when already pinned`() {
        pinnedTiles.value = LinkedHashMap<String, org.atmofox.tv.channels.pinnedtile.PinnedTile>().apply {
            put("https://example.com", CustomPinnedTile("https://example.com", "Example", UUID.randomUUID()))
        }

        val vm = buildViewModel()
        val (states, stateJob) = collectState(vm.state)
        val (events, job) = collectEvents(vm.events)

        vm.pinButtonClicked()

        verify(pinnedTileRepo).removePinnedTile("https://example.com")
        coVerify(exactly = 0) { engineViewCache.captureThumbnail() }
        assertEquals(2, events.size)
        assertTrue(events[0] is ToolbarViewModel.Action.ShowTopToast)
        assertTrue(events[1] is ToolbarViewModel.Action.SetOverlayVisible)
        stateJob.cancel()
        job.cancel()
    }

    @Test
    fun `pinButtonClicked pins with screenshot when not pinned`() {
        coEvery { engineViewCache.captureThumbnail() } returns null

        val vm = buildViewModel()
        val (states, stateJob) = collectState(vm.state)
        val (events, job) = collectEvents(vm.events)

        vm.pinButtonClicked()

        coVerify { engineViewCache.captureThumbnail() }
        verify(pinnedTileRepo).addPinnedTile("https://example.com", null)
        assertEquals(2, events.size)
        assertTrue(events[0] is ToolbarViewModel.Action.ShowTopToast)
        assertTrue(events[1] is ToolbarViewModel.Action.SetOverlayVisible)
        stateJob.cancel()
        job.cancel()
    }

    @Test
    fun `turboButtonClicked toggles turbo mode off`() {
        val vm = buildViewModel()

        vm.turboButtonClicked()

        verify(sessionRepo).setTurboModeEnabled(false, skipEngineSettingsUpdate = false)
        verify(sessionRepo).reload()
    }

    @Test
    fun `turboButtonClicked on homepage skips reload`() {
        sessionState.value = SessionRepo.State(
            backEnabled = false,
            forwardEnabled = false,
            desktopModeActive = false,
            turboModeActive = false,
            currentUrl = "data:text/html,<html></html>",
            loading = false
        )

        val vm = buildViewModel()

        vm.turboButtonClicked()

        verify(sessionRepo).setTurboModeEnabled(true, skipEngineSettingsUpdate = true)
        verify(sessionRepo, never()).reload()
    }

    @Test
    fun `desktopModeButtonClicked toggles desktop mode and emits toast`() {
        val vm = buildViewModel()
        val (events, job) = collectEvents(vm.events)

        vm.desktopModeButtonClicked()

        verify(sessionRepo).setDesktopMode(true)
        assertEquals(2, events.size)
        assertTrue(events[0] is ToolbarViewModel.Action.ShowBottomToast)
        assertTrue(events[1] is ToolbarViewModel.Action.SetOverlayVisible)
        job.cancel()
    }

    @Test
    fun `exitFirefoxButtonClicked emits ExitFirefox`() {
        val vm = buildViewModel()
        val (events, job) = collectEvents(vm.events)

        vm.exitFirefoxButtonClicked()

        assertEquals(1, events.size)
        assertTrue(events[0] is ToolbarViewModel.Action.ExitFirefox)
        job.cancel()
    }
}
