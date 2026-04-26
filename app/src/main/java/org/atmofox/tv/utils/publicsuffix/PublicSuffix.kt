/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils.publicsuffix

import android.content.Context
import android.text.TextUtils
import androidx.annotation.WorkerThread

/**
 * Helper methods for the public suffix part of a domain.
 *
 * A "public suffix" is one under which Internet users can (or historically could) directly register
 * names. Some examples of public suffixes are .com, .co.uk and pvt.k12.ma.us.
 *
 * https://publicsuffix.org/
 *
 * Some parts of the implementation of this class are based on InternetDomainName class of the Guava
 * project: https://github.com/google/guava
 */
object PublicSuffix {

    @JvmStatic
    fun init(context: Context) {
        PublicSuffixKt.init(context)
    }

    /**
     * Strip the public suffix from the domain. Returns the original domain if no public suffix
     * could be found.
     *
     * www.mozilla.org -> www.mozilla
     * independent.co.uk -> independent
     */
    @JvmStatic
    @WorkerThread // This method might need to load data from disk
    fun stripPublicSuffix(context: Context, domain: String?): String {
        if (domain.isNullOrEmpty()) {
            return domain ?: ""
        }

        val index = findPublicSuffixIndex(context, domain)
        if (index == -1) {
            return domain
        }

        return domain.substring(0, index)
    }

    /**
     * Returns the public suffix with the specified number of additional parts.
     *
     * For example, the public suffix of "www.m.bbc.co.uk" (with 0 additional parts) is "co.uk".
     * With 1 additional part: "bbc.co.uk".
     *
     * @throws IllegalArgumentException if additionalPartCount is less than zero.
     * @throws NullPointerException if the Context or domain are null.
     * @return the public suffix with the specified number of additional parts, or the empty string if a public suffix does not exist.
     */
    @JvmStatic
    @WorkerThread // This method might need to load data from disk
    fun getPublicSuffix(context: Context?, domain: String?, additionalPartCount: Int): String {
        if (context == null) {
            throw NullPointerException("Expected non-null Context argument")
        }
        if (domain == null) {
            throw NullPointerException("Expected non-null domain argument")
        }

        if (additionalPartCount < 0) {
            throw IllegalArgumentException("Expected additionalPartCount > 0. Got: $additionalPartCount")
        }

        val publicSuffixCombinedIndex = findPublicSuffixIndex(context, domain)
        if (publicSuffixCombinedIndex < 0) {
            return ""
        }

        val publicSuffix = domain.substring(publicSuffixCombinedIndex + 1) // +1 to remove prefix ".".

        val nextPartIndex = publicSuffix.indexOf('.')
        val publicSuffixFirstPart = if (nextPartIndex < 0) publicSuffix else publicSuffix.substring(0, nextPartIndex)

        val domainParts = normalizeAndSplit(domain)
        val publicSuffixPartsIndex = domainParts.indexOf(publicSuffixFirstPart)
        val returnedPartsIndex = maxOf(0, publicSuffixPartsIndex - additionalPartCount)
        return TextUtils.join(".", domainParts.subList(returnedPartsIndex, domainParts.size))
    }

    /**
     * Returns the index of the leftmost part of the public suffix, or -1 if not found.
     */
    @WorkerThread
    private fun findPublicSuffixIndex(context: Context, domain: String): Int {
        val parts = normalizeAndSplit(domain)
        val partsSize = parts.size
        val exact = PublicSuffixPatterns.getExactSet(context)

        for (i in 0 until partsSize) {
            val ancestorName = TextUtils.join(".", parts.subList(i, partsSize))

            if (exact.contains(ancestorName)) {
                return joinIndex(parts, i)
            }

            // Excluded domains (e.g. !nhs.uk) use the next highest
            // domain as the effective public suffix (e.g. uk).
            if (PublicSuffixPatterns.EXCLUDED.contains(ancestorName)) {
                return joinIndex(parts, i + 1)
            }

            if (matchesWildcardPublicSuffix(ancestorName)) {
                return joinIndex(parts, i)
            }
        }

        return -1
    }

    /**
     * Normalize domain and split into domain parts (www.mozilla.org -> [www, mozilla, org]).
     */
    private fun normalizeAndSplit(domain: String): List<String> {
        var normalized = domain.replace("[.\u3002\uFF0E\uFF61]".toRegex(), ".") // All dot-like characters to '.'
        normalized = normalized.toLowerCase()

        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length - 1) // Strip trailing '.'
        }

        return normalized.split("\\.".toRegex()).filter { it.isNotEmpty() }
    }

    /**
     * Translate the index of the leftmost part of the public suffix to the index of the domain string.
     *
     * [www, mozilla, org] and 2 => 12 (www.mozilla)
     */
    private fun joinIndex(parts: List<String>, index: Int): Int {
        var actualIndex = parts[0].length

        for (i in 1 until index) {
            actualIndex += parts[i].length + 1 // Add one for the "." that is not part of the list elements
        }

        return actualIndex
    }

    /**
     * Does the domain name match one of the "wildcard" patterns (e.g. `*.ar`)?
     */
    private fun matchesWildcardPublicSuffix(domain: String): Boolean {
        val pieces = domain.split("\\.".toRegex(), 2)
        return pieces.size == 2 && PublicSuffixPatterns.UNDER.contains(pieces[1])
    }
}
