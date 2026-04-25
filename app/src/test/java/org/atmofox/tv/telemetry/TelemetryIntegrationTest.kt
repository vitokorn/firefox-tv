/* -*- Mode: Java; c-basic-offset: 4; tab-width: 20; indent-tabs-mode: nil; -*-
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.telemetry

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import mozilla.telemetry.glean.testing.GleanTestRule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.atmofox.tv.utils.anyNonNull
import org.atmofox.tv.GleanMetrics.Telemetry as TelemetryMetrics
import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner

@RunWith(FirefoxRobolectricTestRunner::class)
class TelemetryIntegrationTest {
    @get:Rule
    val gleanRule = GleanTestRule(ApplicationProvider.getApplicationContext())

    private lateinit var appContext: Application
    private lateinit var telemetryIntegration: TelemetryIntegration
    private lateinit var sentrySpy: SentryIntegration

    @Before
    fun setup() {
        appContext = ApplicationProvider.getApplicationContext()
        sentrySpy = spy(SentryIntegration)
        telemetryIntegration = TestTelemetryIntegration(sentrySpy)
    }

    @Test
    fun `WHEN startSession and stopSession are called THEN Glean records the session events`() {
        telemetryIntegration.startSession(appContext)
        val startEvents = TelemetryMetrics.telemetryEvent.testGetValue()!!
        assertEquals(1, startEvents.size)
        assertEquals("session_start", startEvents[0].name)

        telemetryIntegration.stopSession(appContext)
        val events = TelemetryMetrics.telemetryEvent.testGetValue()!!
        assertEquals(3, events.size)
        assertEquals("session_start", events[0].name)
        assertEquals("session_stop", events[1].name)
        assertEquals("home_tile_unique_click_count", events[2].name)
        assertEquals("0", events[2].extra?.getValue("total"))
    }

    @Test
    fun `WHEN TelemetryWrapper is called out of order THEN sentry should capture callstack`() {
        telemetryIntegration.stopSession(appContext)
        telemetryIntegration.startSession(appContext)

        verify(sentrySpy, times(1)).capture(anyNonNull())
    }

    @Test
    fun `GIVEN session is running WHEN stopSession is called twice in a row THEN sentry should capture callstack`() {
        telemetryIntegration.startSession(appContext)
        telemetryIntegration.stopSession(appContext)
        telemetryIntegration.stopSession(appContext)

        verify(sentrySpy, times(1)).capture(anyNonNull())
    }
}

/**
 * Allows us to pass a non-default value for [SentryIntegration] for testing
 * purposes
 */
private class TestTelemetryIntegration(
    sentryIntegration: SentryIntegration
) : TelemetryIntegration(sentryIntegration)
