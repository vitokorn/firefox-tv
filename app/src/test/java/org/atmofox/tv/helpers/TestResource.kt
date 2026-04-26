/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.helpers

/** Accessors to resources used in testing. These files are available in `app/src/test/resources`. */
enum class TestResource(private val path: String) {
    ;

    fun get(): String = this::class.java.classLoader!!.getResource(path)!!.readText()
}
