/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils.publicsuffix

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

internal object PublicSuffixPatterns {
    /** If a hostname is contained as a key in this map, it is a public suffix. */
    private var EXACT: MutableSet<String>? = null

    @JvmStatic
    @Synchronized
    fun getExactSet(context: Context): Set<String> {
        EXACT?.let { return it }

        val exact = HashSet<String>()

        var reader: BufferedReader? = null
        try {
            reader = BufferedReader(InputStreamReader(
                BufferedInputStream(context.assets.open("publicsuffixlist"))))

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                exact.add(line!!)
            }
        } catch (e: IOException) {
            throw IllegalStateException("resource publicsuffixlist could not be opened but is bundled with app", e)
        } finally {
            try {
                reader?.close()
            } catch (e: IOException) { }
        }

        EXACT = exact
        return exact
    }

    /**
     * If a hostname is not a key in the EXCLUDE map, and if removing its
     * leftmost component results in a name which is a key in this map, it is a
     * public suffix.
     */
    @JvmField
    val UNDER: MutableSet<String> = HashSet<String>().apply {
        add("bd")
        add("magentosite.cloud")
        add("ke")
        add("triton.zone")
        add("compute.estate")
        add("ye")
        add("pg")
        add("kh")
        add("platform.sh")
        add("fj")
        add("ck")
        add("fk")
        add("alces.network")
        add("sch.uk")
        add("jm")
        add("mm")
        add("api.githubcloud.com")
        add("ext.githubcloud.com")
        add("0emm.com")
        add("githubcloudusercontent.com")
        add("cns.joyent.com")
        add("bn")
        add("yokohama.jp")
        add("nagoya.jp")
        add("kobe.jp")
        add("sendai.jp")
        add("kawasaki.jp")
        add("sapporo.jp")
        add("kitakyushu.jp")
        add("np")
        add("nom.br")
        add("er")
        add("cryptonomic.net")
        add("gu")
        add("kw")
        add("zw")
        add("mz")
    }

    /**
     * The elements in this map would pass the UNDER test, but are known not to
     * be public suffixes and are thus excluded from consideration. Since it
     * refers to elements in UNDER of the same type, the type is actually not
     * important here. The map is simply used for consistency reasons.
     */
    @JvmField
    val EXCLUDED: MutableSet<String> = HashSet<String>().apply {
        add("www.ck")
        add("city.yokohama.jp")
        add("city.nagoya.jp")
        add("city.kobe.jp")
        add("city.sendai.jp")
        add("city.kawasaki.jp")
        add("city.sapporo.jp")
        add("city.kitakyushu.jp")
        add("teledata.mz")
    }
}
