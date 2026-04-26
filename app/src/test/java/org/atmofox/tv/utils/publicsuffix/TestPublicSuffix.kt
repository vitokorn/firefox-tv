/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, you can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils.publicsuffix

import org.atmofox.tv.helpers.FirefoxRobolectricTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(FirefoxRobolectricTestRunner::class)
class TestPublicSuffix {
    @Test
    fun testStripPublicSuffix() {
        // Test empty value
        Assert.assertEquals(
            "",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "")
        )

        // Test domains with public suffix
        Assert.assertEquals(
            "www.mozilla",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "www.mozilla.org")
        )
        Assert.assertEquals(
            "www.google",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "www.google.com")
        )
        Assert.assertEquals(
            "foobar",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "foobar.blogspot.com")
        )
        Assert.assertEquals(
            "independent",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "independent.co.uk")
        )
        Assert.assertEquals(
            "biz",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "biz.com.ua")
        )
        Assert.assertEquals(
            "example",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "example.org")
        )
        Assert.assertEquals(
            "example",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "example.pvt.k12.ma.us")
        )

        // Test domain without public suffix
        Assert.assertEquals(
            "localhost",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "localhost")
        )
        Assert.assertEquals(
            "firefox.mozilla",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "firefox.mozilla")
        )

        // IDN domains
        Assert.assertEquals(
            "ουτοπία.δπθ",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "ουτοπία.δπθ.gr")
        )
        Assert.assertEquals(
            "a网络A",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "a网络A.网络.Cn")
        )

        // Other non-domain values
        Assert.assertEquals(
            "192.168.0.1",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "192.168.0.1")
        )
        Assert.assertEquals(
            "asdflkj9uahsd",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "asdflkj9uahsd")
        )

        // Other trailing and other types of dots
        Assert.assertEquals(
            "www.mozilla。home．example",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "www.mozilla。home．example｡org")
        )
        Assert.assertEquals(
            "example",
            PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, "example.org")
        )
    }

    @Test
    fun testStripPublicSuffixWithNullDomain() {
        val result = PublicSuffix.stripPublicSuffix(RuntimeEnvironment.application, null)
        Assert.assertEquals("", result)
    }

    @Test
    fun testGetPublicSuffixZeroAdditionalParts() {
        val inputToExpected = hashMapOf(
            "" to "",
            " " to "",
            "www.mozilla.org" to "org",
            "www.google.com" to "com",
            "foobar.blogspot.com" to "blogspot.com",
            "independent.co.uk" to "co.uk",
            "biz.com.ua" to "com.ua",
            "example.org" to "org",
            "example.pvt.k12.ma.us" to "pvt.k12.ma.us",
            "localhost" to "",
            "firefox.mozilla" to "",
            "ουτοπία.δπθ.gr" to "gr",
            "a网络A.网络.cn" to "网络.cn",
            "192.168.0.1" to "",
            "asdflkj9uahsd" to "",
            "www.mozilla。home．example｡org" to "org",
            "example.org" to "org"
        )

        for ((input, expected) in inputToExpected) {
            Assert.assertEquals(
                "for input:$input||",
                expected,
                PublicSuffix.getPublicSuffix(RuntimeEnvironment.application, input, 0)
            )
        }
    }

    @Test
    fun testGetPublicSuffixNonZeroAdditionalParts() {
        val inputToExpected = hashMapOf(
            "www.mozilla.org" to "mozilla.org",
            "www.google.com" to "google.com",
            "bbc.co.uk" to "bbc.co.uk",
            "www.bbc.co.uk" to "bbc.co.uk"
        )

        for ((input, expected) in inputToExpected) {
            Assert.assertEquals(
                "for input:$input||",
                expected,
                PublicSuffix.getPublicSuffix(RuntimeEnvironment.application, input, 1)
            )
        }

        // More than one additional part.
        Assert.assertEquals(
            "m.bbc.co.uk",
            PublicSuffix.getPublicSuffix(RuntimeEnvironment.application, "www.m.bbc.co.uk", 2)
        )

        // Look for more additional parts than exist in the host: the full host should be returned.
        val inputsAndExpecteds = arrayOf(
            "google.com",
            "www.google.com",
            "bbc.co.uk"
        )

        for (inputAndExpected in inputsAndExpecteds) {
            Assert.assertEquals(
                inputAndExpected,
                PublicSuffix.getPublicSuffix(RuntimeEnvironment.application, inputAndExpected, 100)
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetPublicSuffixWithNegativeAdditionalPartCountThrows() {
        PublicSuffix.getPublicSuffix(RuntimeEnvironment.application, "whatever-doesnt-matter", -1)
    }

    @Test(expected = NullPointerException::class)
    fun testGetPublicSuffixWithNullContextThrows() {
        PublicSuffix.getPublicSuffix(null, "whatever", 0)
    }

    @Test(expected = NullPointerException::class)
    fun testGetPublicSuffixWithNullDomainThrows() {
        PublicSuffix.getPublicSuffix(RuntimeEnvironment.application, null, 0)
    }
}
