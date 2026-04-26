package org.atmofox.tv.utils

import org.atmofox.tv.BuildConfig
import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(FirefoxRobolectricTestRunner::class)
class SupportUtilsTest {

    @Test
    fun cleanup() {
        // Other tests might get confused by our locale fiddling, so lets go back to the default:
        Locale.setDefault(Locale.ENGLISH)
    }

    /*
     * Super simple sumo URL test - it exists primarily to verify that we're setting the language
     * and page tags correctly. appVersion is null in tests, so we just test that there's a null there,
     * which doesn't seem too useful...
     */
    @Test
    @Throws(Exception::class)
    fun getSumoURLForTopic() {
        val version = BuildConfig.VERSION_NAME
        Locale.setDefault(Locale.GERMANY)
        assertEquals(
            "https://support.mozilla.org/1/mobile/$version/Android/de-DE/foobar",
            SupportUtils.getSumoURLForTopic(RuntimeEnvironment.application, "foobar")
        )

        Locale.setDefault(Locale.CANADA_FRENCH)
        assertEquals(
            "https://support.mozilla.org/1/mobile/$version/Android/fr-CA/foobar",
            SupportUtils.getSumoURLForTopic(RuntimeEnvironment.application, "foobar")
        )
    }

    /**
     * This is a pretty boring tests - it exists primarily to verify that we're actually setting
     * a langtag in the manfiesto URL.
     */
    @Test
    @Throws(Exception::class)
    fun getManifestoURL() {
        Locale.setDefault(Locale.UK)
        assertEquals(
            "https://www.mozilla.org/en-GB/about/manifesto/",
            SupportUtils.getManifestoURL()
        )

        Locale.setDefault(Locale.KOREA)
        assertEquals(
            "https://www.mozilla.org/ko-KR/about/manifesto/",
            SupportUtils.getManifestoURL()
        )
    }
}
