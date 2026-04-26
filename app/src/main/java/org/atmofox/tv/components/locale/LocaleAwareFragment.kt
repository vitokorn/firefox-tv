/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.components.locale

import androidx.fragment.app.Fragment
import java.util.Locale

abstract class LocaleAwareFragment : Fragment() {
    private var cachedLocale: Locale? = null

    /**
     * Is called whenever the application locale has changed. Your fragment must either update
     * all localised Strings, or replace itself with an updated version.
     */
    abstract fun applyLocale()

    override fun onResume() {
        super.onResume()

        LocaleManager.getInstance()
            .correctLocale(context!!, resources, resources.configuration)

        if (cachedLocale == null) {
            cachedLocale = Locale.getDefault()
        } else {
            var newLocale = LocaleManager.getInstance().getCurrentLocale(activity!!.applicationContext)

            if (newLocale == null) {
                // Using system locale:
                newLocale = Locale.getDefault()
            }
            if (newLocale != cachedLocale) {
                cachedLocale = newLocale
                applyLocale()
            }
        }
    }
}
