/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.webrender

import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.atmofox.tv.R

class FirefoxProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    private val progressAnimationView: ImageView
        get() = findViewById(R.id.progressAnimation)

    private val urlView: TextView
        get() = findViewById(R.id.url)

    fun updateProgress(loading: Boolean, url: String) {
        Log.d("FirefoxProgressBar", "updateProgress: loading=$loading, url=$url, visibility=$visibility")
        if (loading) {
            showBar()
        } else {
            hideBar()
        }
        urlView.text = url
    }

    init {
        LayoutInflater.from(context)
                .inflate(R.layout.firefox_progress_bar, this, true)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        visibility = View.GONE
        alpha = 0f
    }

    private fun showBar() {
        Log.d("FirefoxProgressBar", "showBar: visibility was $visibility")
        visibility = View.VISIBLE
        (progressAnimationView.background as AnimationDrawable).start()
        animate().cancel()
        alpha = 1f
    }

    private fun hideBar() {
        Log.d("FirefoxProgressBar", "hideBar: visibility was $visibility")
        animate().cancel()
        (progressAnimationView.background as AnimationDrawable).stop()
        visibility = View.GONE
        alpha = 0f
    }
}
