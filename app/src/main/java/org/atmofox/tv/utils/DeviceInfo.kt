/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.utils

import android.os.Build

/**
 * Contains information about the device the app is running on.
 */
class DeviceInfo {

    /**
     * Return the device model name.
     */
    fun getDeviceModel(): String {
        return Build.MODEL
    }
}
