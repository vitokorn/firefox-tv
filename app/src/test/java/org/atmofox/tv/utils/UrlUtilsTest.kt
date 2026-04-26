package org.atmofox.tv.utils

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner

@RunWith(FirefoxRobolectricTestRunner::class)
class UrlUtilsTest {
    @Test
    fun isValidSearchQueryUrl() {
        assertTrue(UrlUtils.isValidSearchQueryUrl("https://example.com/search/?q=%s"))
        assertTrue(UrlUtils.isValidSearchQueryUrl("http://example.com/search/?q=%s"))
        assertTrue(UrlUtils.isValidSearchQueryUrl("http-test-site.com/search/?q=%s"))
        assertFalse(UrlUtils.isValidSearchQueryUrl("httpss://example.com/search/?q=%s"))

        assertTrue(UrlUtils.isValidSearchQueryUrl("example.com/search/?q=%s"))
        assertTrue(UrlUtils.isValidSearchQueryUrl(" example.com/search/?q=%s "))

        assertFalse(UrlUtils.isValidSearchQueryUrl("htps://example.com/search/?q=%s"))

        assertFalse(UrlUtils.isValidSearchQueryUrl(" example.com/search/?q= "))
    }

    @Test
    @Throws(Exception::class)
    fun urlsMatchExceptForTrailingSlash() {
        assertTrue(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.mozilla.org", "http://www.mozilla.org"))
        assertTrue(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.mozilla.org/", "http://www.mozilla.org"))
        assertTrue(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.mozilla.org", "http://www.mozilla.org/"))

        assertFalse(UrlUtils.urlsMatchExceptForTrailingSlash("http://mozilla.org", "http://www.mozilla.org"))
        assertFalse(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.mozilla.org/", "http://mozilla.org"))

        assertFalse(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.mozilla.org", "https://www.mozilla.org"))
        assertFalse(UrlUtils.urlsMatchExceptForTrailingSlash("https://www.mozilla.org", "http://www.mozilla.org"))

        // Same length of domain, but otherwise different:
        assertFalse(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.allizom.org", "http://www.mozilla.org"))
        assertFalse(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.allizom.org/", "http://www.mozilla.org"))
        assertFalse(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.allizom.org", "http://www.mozilla.org/"))

        // Check upper/lower case is OK:
        assertTrue(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.MOZILLA.org", "http://www.mozilla.org"))
        assertTrue(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.MOZILLA.org/", "http://www.mozilla.org"))
        assertTrue(UrlUtils.urlsMatchExceptForTrailingSlash("http://www.MOZILLA.org", "http://www.mozilla.org/"))
    }

    @Test
    fun isPermittedResourceProtocol() {
        assertFalse(UrlUtils.isPermittedResourceProtocol(""))
        assertFalse(UrlUtils.isPermittedResourceProtocol(null))

        assertTrue(UrlUtils.isPermittedResourceProtocol("http"))
        assertTrue(UrlUtils.isPermittedResourceProtocol("https"))

        assertTrue(UrlUtils.isPermittedResourceProtocol("data"))
        assertTrue(UrlUtils.isPermittedResourceProtocol("file"))

        assertFalse(UrlUtils.isPermittedResourceProtocol("nielsenwebid"))
    }

    @Test
    fun isPermittedProtocol() {
        assertFalse(UrlUtils.isSupportedProtocol(""))
        assertFalse(UrlUtils.isSupportedProtocol(null))

        assertTrue(UrlUtils.isSupportedProtocol("http"))
        assertTrue(UrlUtils.isSupportedProtocol("https"))
        assertTrue(UrlUtils.isSupportedProtocol("error"))
        assertTrue(UrlUtils.isSupportedProtocol("data"))

        assertFalse(UrlUtils.isSupportedProtocol("market"))
    }

    @Test
    fun testIsUrl() {
        assertTrue(UrlUtils.isUrl("http://www.mozilla.org"))
        assertTrue(UrlUtils.isUrl("https://www.mozilla.org"))
        assertTrue(UrlUtils.isUrl("https://www.mozilla.org "))
        assertTrue(UrlUtils.isUrl(" https://www.mozilla.org"))
        assertTrue(UrlUtils.isUrl(" https://www.mozilla.org "))
        assertTrue(UrlUtils.isUrl("https://www.mozilla.org/en-US/internet-health/"))
        assertTrue(UrlUtils.isUrl("file:///mnt/sdcard/"))
        assertTrue(UrlUtils.isUrl("mozilla.org"))

        assertFalse(UrlUtils.isUrl("Hello World"))
        assertFalse(UrlUtils.isUrl("Mozilla"))
    }

    @Test
    fun testNormalize() {
        assertEquals("http://www.mozilla.org", UrlUtils.normalize("http://www.mozilla.org"))
        assertEquals("https://www.mozilla.org", UrlUtils.normalize("https://www.mozilla.org"))
        assertEquals("https://www.mozilla.org/en-US/internet-health/", UrlUtils.normalize("https://www.mozilla.org/en-US/internet-health/"))
        assertEquals("file:///mnt/sdcard/", UrlUtils.normalize("file:///mnt/sdcard/"))

        assertEquals("http://mozilla.org", UrlUtils.normalize("mozilla.org"))
        assertEquals("http://mozilla.org", UrlUtils.normalize("http://mozilla.org "))
        assertEquals("http://mozilla.org", UrlUtils.normalize(" http://mozilla.org "))
        assertEquals("http://mozilla.org", UrlUtils.normalize(" http://mozilla.org"))
        assertEquals("http://localhost", UrlUtils.normalize("localhost"))
    }

    @Test
    fun testIsSearchQuery() {
        assertTrue(UrlUtils.isSearchQuery("hello world"))

        assertFalse(UrlUtils.isSearchQuery("mozilla.org"))
        assertFalse(UrlUtils.isSearchQuery("mozilla"))
    }

    @Test
    fun testCreateSearchUrl() {
        // java.net.URLEncoder.encode produces + for spaces (application/x-www-form-urlencoded)
        val searchString1 = UrlUtils.createSearchUrl(ApplicationProvider.getApplicationContext(), "dogs are cool")
        val searchString2 = UrlUtils.createSearchUrl(ApplicationProvider.getApplicationContext(), "how can mirrors be real if our eyes arent real?")

        // Verify the search terms are properly encoded
        assertTrue("Search URL should contain encoded term", searchString1.contains("dogs+are+cool") || searchString1.contains("dogs%20are%20cool"))
        assertTrue("Search URL should contain encoded term", searchString2.contains("how+can+mirrors+be+real") || searchString2.contains("how%20can%20mirrors%20be%20real"))

        // Verify the URLs contain the search query parameter
        assertTrue("Search URL should contain q= parameter", searchString1.contains("q="))
        assertTrue("Search URL should contain q= parameter", searchString2.contains("q="))
    }

    @Test
    fun `WHEN input has no user info THEN should return unchanged`() {
        listOf(
                "",
                "öäü102ß",
                "user@mozilla.org",
                "https://user:password@www.uri-contains-%-percentage-marks-that-are-not-associated-with-numbers.org%",
                "://user:password@i/have/no/scheme.org"
        ).forEach {
            val transformed = UrlUtils.stripUserInfo(it)
            assertEquals(it, transformed)
        }
    }

    @Test
    fun `WHEN null is passed THEN should return blank`() {
        assertEquals("", UrlUtils.stripUserInfo(null))
    }

    @Test
    fun `WHEN user info is included THEN should return url without user info`() {
        fun String.stripped(): String {
            return UrlUtils.stripUserInfo(this)
        }
        assertEquals("https://www.mozilla.org", "https://user:password@www.mozilla.org".stripped())
        assertEquals("https://www.mozilla.org", "https://user@www.mozilla.org".stripped())
        assertEquals("ftp://mozilla.org", "ftp://user:password@mozilla.org".stripped())
    }

    @Test
    fun isInternalErrorURL() {
        assertTrue(UrlUtils.isInternalErrorURL("data:text/html;charset=utf-8;base64,"))

        assertFalse(UrlUtils.isInternalErrorURL("http://www.mozilla.org"))
        assertFalse(UrlUtils.isInternalErrorURL("https://www.mozilla.org/en-us/about"))
        assertFalse(UrlUtils.isInternalErrorURL("www.mozilla.org"))
        assertFalse(UrlUtils.isInternalErrorURL("error:-8"))
        assertFalse(UrlUtils.isInternalErrorURL("hello world"))
    }

    @Test
    fun isHttpOrHttpsUrl() {
        assertFalse(UrlUtils.isHttpOrHttps(null))
        assertFalse(UrlUtils.isHttpOrHttps(""))
        assertFalse(UrlUtils.isHttpOrHttps("     "))
        assertFalse(UrlUtils.isHttpOrHttps("mozilla.org"))
        assertFalse(UrlUtils.isHttpOrHttps("httpstrf://example.org"))

        assertTrue(UrlUtils.isHttpOrHttps("https://www.mozilla.org"))
        assertTrue(UrlUtils.isHttpOrHttps("http://example.org"))
        assertTrue(UrlUtils.isHttpOrHttps("http://192.168.0.1"))
    }

    @Test
    fun testStripCommonSubdomains() {
        assertEquals("mozilla.org", UrlUtils.stripCommonSubdomains("mozilla.org"))
        assertEquals("mozilla.org", UrlUtils.stripCommonSubdomains("www.mozilla.org"))
        assertEquals("mozilla.org", UrlUtils.stripCommonSubdomains("m.mozilla.org"))
        assertEquals("mozilla.org", UrlUtils.stripCommonSubdomains("mobile.mozilla.org"))
        assertEquals("random.mozilla.org", UrlUtils.stripCommonSubdomains("random.mozilla.org"))
        assertEquals(null, UrlUtils.stripCommonSubdomains(null))
    }

    @Test
    fun `GIVEN input is not 'home' WHEN input is transformed THEN it should be unchanged`() {
        listOf(
                "error:-8",
                "hello world",
                "http://example.org",
                "http://192.168.0.1",
                "mozilla.org",
                "www.mozilla.org",
                "m.mozilla.org",
                "mobile.mozilla.org"
        ).forEach {
            val itTransformed = UrlUtils.toUrlBarDisplay(it)
            assertEquals(it, itTransformed)
        }
    }

    @Test
    fun `GIVEN input is 'home' WHEN input is transformed THEN it should return blank`() {
        assertEquals("", UrlUtils.toUrlBarDisplay(URLs.APP_URL_HOME))
    }
}
