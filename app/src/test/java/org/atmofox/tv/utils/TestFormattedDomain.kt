/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils

import android.text.TextUtils
import androidx.annotation.NonNull
import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner
import org.junit.Assert
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

@RunWith(FirefoxRobolectricTestRunner::class)
class TestFormattedDomain {

    private val BUGZILLA_URL = "https://bugzilla.mozilla.org/enter_bug.cgi?format=guided#h=dupes%7CData%20%26%20BI%20Services%20Team%7C"

    companion object {
        private val EMPTY_PATH = Pattern.compile("/*")

        /**
         * Returns true if [URI.getPath] is not empty, false otherwise where empty means the given path contains
         * characters other than "/".
         *
         * This is necessary because the URI method will return "/" for "http://google.com/".
         */
        @JvmStatic
        fun isPathEmpty(@NonNull uri: URI): Boolean {
            val path = uri.path
            return TextUtils.isEmpty(path) || EMPTY_PATH.matcher(path).matches()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIsPathEmptyWithURINoPath() {
        val uri = URI("https://google.com")
        Assert.assertTrue(isPathEmpty(uri))
    }

    @Test
    @Throws(Exception::class)
    fun testIsPathEmptyWithURISlashPath() {
        val uri = URI("http://google.com/")
        Assert.assertTrue(isPathEmpty(uri))
    }

    @Test
    @Throws(Exception::class)
    fun testIsPathEmptyWithURIDoubleSlashPath() {
        val uri = URI("http://google.com//")
        Assert.assertTrue(isPathEmpty(uri))
    }

    @Test
    @Throws(Exception::class)
    fun testIsPathEmptyWithURIEncodedSpaceSlashPath() {
        val uri = URI("http://google.com/%20/")
        Assert.assertFalse(isPathEmpty(uri))
    }

    @Test
    @Throws(Exception::class)
    fun testIsPathEmptyWithURIPath() {
        val uri = URI("http://google.com/search/whatever/")
        Assert.assertFalse(isPathEmpty(uri))
    }

    // --- format, include PublicSuffix --- //
    @Test
    fun testGetFormattedDomainWithSuffix0Parts() {
        val includePublicSuffix = true
        val subdomainCount = 0
        assertGetFormattedDomain("https://google.com/search", includePublicSuffix, subdomainCount, "google.com")
        assertGetFormattedDomain("https://www.example.com/index.html", includePublicSuffix, subdomainCount, "example.com")
        assertGetFormattedDomain("https://m.blog.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "foo.com")
        assertGetFormattedDomain("https://user:pass@m.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "foo.com")
    }

    @Test
    fun testGetFormattedDomainWithSuffix1Parts() {
        val includePublicSuffix = true
        val subdomainCount = 1
        assertGetFormattedDomain("https://google.com/search", includePublicSuffix, subdomainCount, "google.com")
        assertGetFormattedDomain("https://www.example.com/index.html", includePublicSuffix, subdomainCount, "www.example.com")
        assertGetFormattedDomain("https://m.blog.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "blog.foo.com")
        assertGetFormattedDomain("https://user:pass@m.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "m.foo.com")
    }

    @Test
    fun testGetFormattedDomainWithSuffix2Parts() {
        val includePublicSuffix = true
        val subdomainCount = 2
        assertGetFormattedDomain("https://google.com/search", includePublicSuffix, subdomainCount, "google.com")
        assertGetFormattedDomain("https://www.example.com/index.html", includePublicSuffix, subdomainCount, "www.example.com")
        assertGetFormattedDomain("https://m.blog.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "m.blog.foo.com")
        assertGetFormattedDomain("https://user:pass@m.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "m.foo.com")
    }

    // --- format, exclude PublicSuffix --- //
    @Test
    fun testGetFormattedDomainNoSuffix0Parts() {
        val includePublicSuffix = false
        val subdomainCount = 0
        assertGetFormattedDomain("https://google.com/search", includePublicSuffix, subdomainCount, "google")
        assertGetFormattedDomain("https://www.example.com/index.html", includePublicSuffix, subdomainCount, "example")
        assertGetFormattedDomain("https://m.blog.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "foo")
        assertGetFormattedDomain("https://user:pass@m.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "foo")
    }

    @Test
    fun testGetFormattedDomainNoSuffix1Parts() {
        val includePublicSuffix = false
        val subdomainCount = 1
        assertGetFormattedDomain("https://google.com/search", includePublicSuffix, subdomainCount, "google")
        assertGetFormattedDomain("https://www.example.com/index.html", includePublicSuffix, subdomainCount, "www.example")
        assertGetFormattedDomain("https://m.blog.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "blog.foo")
        assertGetFormattedDomain("https://user:pass@m.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "m.foo")
    }

    @Test
    fun testGetFormattedDomainNoSuffix2Parts() {
        val includePublicSuffix = false
        val subdomainCount = 2
        assertGetFormattedDomain("https://google.com/search", includePublicSuffix, subdomainCount, "google")
        assertGetFormattedDomain("https://www.example.com/index.html", includePublicSuffix, subdomainCount, "www.example")
        assertGetFormattedDomain("https://m.blog.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "m.blog.foo")
        assertGetFormattedDomain("https://user:pass@m.foo.com/bar/baz?noo=abc#123", includePublicSuffix, subdomainCount, "m.foo")
    }

    // --- format, saving time by not splitting up these tests on public suffix param. --- //
    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainTwoLevelPublicSuffix() {
        assertGetFormattedDomain("http://bbc.co.uk", false, 0, "bbc")
        assertGetFormattedDomain("http://bbc.co.uk", true, 0, "bbc.co.uk")
    }

    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainNormalTwoLevelPublicSuffixWithSubdomain() {
        assertGetFormattedDomain("http://a.bbc.co.uk", false, 0, "bbc")
        assertGetFormattedDomain("http://a.bbc.co.uk", true, 0, "bbc.co.uk")
        assertGetFormattedDomain(BUGZILLA_URL, false, 0, "mozilla")
        assertGetFormattedDomain(BUGZILLA_URL, true, 0, "mozilla.org")
    }

    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainWilcardDomain() {
        // TLD entry: *.kawasaki.jp
        assertGetFormattedDomain("http://a.b.kawasaki.jp", false, 0, "a")
        assertGetFormattedDomain("http://a.b.kawasaki.jp", true, 0, "a.b.kawasaki.jp")
    }

    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainWilcardDomainWithAdditionalSubdomain() {
        // TLD entry: *.kawasaki.jp
        assertGetFormattedDomain("http://a.b.c.kawasaki.jp", false, 0, "b")
        assertGetFormattedDomain("http://a.b.c.kawasaki.jp", true, 0, "b.c.kawasaki.jp")
    }

    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainExceptionDomain() {
        // TLD entry: !city.kawasaki.jp
        assertGetFormattedDomain("http://city.kawasaki.jp", false, 0, "city")
        assertGetFormattedDomain("http://city.kawasaki.jp", true, 0, "city.kawasaki.jp")
    }

    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainExceptionDomainWithAdditionalSubdomain() {
        // TLD entry: !city.kawasaki.jp
        assertGetFormattedDomain("http://a.city.kawasaki.jp", false, 0, "city")
        assertGetFormattedDomain("http://a.city.kawasaki.jp", true, 0, "city.kawasaki.jp")
    }

    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainExceptionDomainBugzillaURL() {
        // TLD entry: !city.kawasaki.jp
        assertGetFormattedDomain("http://a.city.kawasaki.jp", false, 0, "city")
        assertGetFormattedDomain("http://a.city.kawasaki.jp", true, 0, "city.kawasaki.jp")
    }

    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainURIHasNoHost() {
        assertGetFormattedDomain("file:///usr/bin", false, 0, "")
        assertGetFormattedDomain("file:///usr/bin", true, 0, "")
    }

    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainIPv4() {
        assertGetFormattedDomain("http://192.168.1.1", false, 0, "192.168.1.1")
        assertGetFormattedDomain("http://192.168.1.1", true, 0, "192.168.1.1")
    }

    @Test
    @Throws(Exception::class)
    fun testGetFormattedDomainIPv6() {
        assertGetFormattedDomain("http://[3ffe:1900:4545:3:200:f8ff:fe21:67cf]", false, 0, "[3ffe:1900:4545:3:200:f8ff:fe21:67cf]")
        assertGetFormattedDomain("http://[3ffe:1900:4545:3:200:f8ff:fe21:67cf]", true, 0, "[3ffe:1900:4545:3:200:f8ff:fe21:67cf]")
    }

    @Test(expected = NullPointerException::class)
    @Throws(Exception::class)
    fun testGetFormattedDomainNullContextThrows() {
        FormattedDomain.format(null, URI("http://google.com"), false, 0)
    }

    @Test(expected = NullPointerException::class)
    @Throws(Exception::class)
    fun testGetFormattedDomainNullURIThrows() {
        FormattedDomain.format(RuntimeEnvironment.application, null, false, 0)
    }

    private fun assertGetFormattedDomain(
        uriString: String,
        includePublicSuffix: Boolean,
        subdomainCount: Int,
        expected: String
    ) {
        val uri = try {
            URI(uriString)
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("Invalid URI passed into test: $uriString")
        }

        Assert.assertEquals(
            "for input:$uriString||",
            expected,
            FormattedDomain.format(RuntimeEnvironment.application, uri, includePublicSuffix, subdomainCount)
        )
    }

    @Test
    fun testIsIPv4RealAddress() {
        assertTrue(FormattedDomain.isIPv4("192.168.1.1"))
        assertTrue(FormattedDomain.isIPv4("8.8.8.8"))
        assertTrue(FormattedDomain.isIPv4("63.245.215.20"))
    }

    @Test
    fun testIsIPv4WithProtocol() {
        assertFalse(FormattedDomain.isIPv4("http://8.8.8.8"))
        assertFalse(FormattedDomain.isIPv4("https://8.8.8.8"))
    }

    @Test
    fun testIsIPv4WithPort() {
        assertFalse(FormattedDomain.isIPv4("8.8.8.8:400"))
        assertFalse(FormattedDomain.isIPv4("8.8.8.8:1337"))
    }

    @Test
    fun testIsIPv4WithPath() {
        assertFalse(FormattedDomain.isIPv4("8.8.8.8/index.html"))
        assertFalse(FormattedDomain.isIPv4("8.8.8.8/"))
    }

    @Test
    fun testIsIPv4WithIPv6() {
        assertFalse(FormattedDomain.isIPv4("2001:db8::1 "))
        assertFalse(FormattedDomain.isIPv4("2001:db8:0:1:1:1:1:1"))
        assertFalse(FormattedDomain.isIPv4("[2001:db8:a0b:12f0::1]"))
    }
}
