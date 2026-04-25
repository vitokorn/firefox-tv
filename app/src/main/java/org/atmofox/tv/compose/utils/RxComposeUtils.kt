/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.utils

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.Consumer

/**
 * Collects an RxJava2 [Observable] into a Compose [State].
 *
 * Automatically switches to the main thread before updating state,
 * since Compose [MutableState] must only be mutated on the main thread.
 *
 * @param initial The initial value before the first emission.
 */
@Composable
fun <T : Any> Observable<T>.collectAsState(initial: T): State<T> {
    val state = remember { mutableStateOf(initial) }
    DisposableEffect(this) {
        val disposable = observeOn(AndroidSchedulers.mainThread()).subscribe(
            Consumer { state.value = it },
            Consumer { Log.e("collectAsState", "Error collecting state", it) }
        )
        onDispose { disposable.dispose() }
    }
    return state
}
