/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import org.atmofox.tv.R

/**
 * A helper class to implement a ViewGroup that resizes dynamically (by adding padding to the bottom)
 * based on whether a keyboard is visible or not.
 *
 * Implementation based on:
 * https://github.com/mikepenz/MaterialDrawer/blob/master/library/src/main/java/com/mikepenz/materialdrawer/util/KeyboardUtil.java
 *
 * An optional viewToHideWhenActivated can be set: this is a View that will be hidden when the keyboard
 * is showing. That can be useful for things like FABs that you don't need when someone is typing.
 *
 * A View using this delegate needs to forward the calls to onAttachedToWindow() and onDetachedFromWindow()
 * to this class.
 */
internal class ResizableKeyboardViewDelegate(
    private val delegateView: View,
    attrs: android.util.AttributeSet?
) {
    private val rect = Rect()
    private var decorView: View? = null

    private var idOfViewToHide: Int = -1
    private var viewToHide: View? = null
    private var shouldAnimate: Boolean = false
    private var isAnimating: Boolean = false

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (isAnimating) {
            return@OnGlobalLayoutListener
        }

        val difference = calculateDifferenceBetweenHeightAndUsableArea()

        // If difference > 0, keyboard is showing.
        // If difference =< 0, keyboard is not showing or is in multiview mode.
        if (difference > 0) {
            // Keyboard showing -> Set difference has bottom padding.
            if (delegateView.paddingBottom != difference) {
                updateBottomPadding(difference)
                viewToHide?.visibility = View.GONE
            }
        } else {
            // Keyboard not showing -> Reset bottom padding.
            if (delegateView.paddingBottom != 0) {
                updateBottomPadding(0)
                viewToHide?.visibility = View.VISIBLE
            }
        }
    }

    init {
        val styleAttributeArray = delegateView.context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.ResizableKeyboardViewDelegate,
            0, 0
        )

        try {
            idOfViewToHide = styleAttributeArray.getResourceId(
                R.styleable.ResizableKeyboardViewDelegate_viewToHideWhenActivated,
                -1
            )
            shouldAnimate = styleAttributeArray.getBoolean(
                R.styleable.ResizableKeyboardViewDelegate_animate,
                false
            )
        } finally {
            styleAttributeArray.recycle()
        }
    }

    fun onAttachedToWindow() {
        delegateView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        if (idOfViewToHide != -1) {
            viewToHide = delegateView.findViewById(idOfViewToHide)
        }
    }

    fun onDetachedFromWindow() {
        delegateView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        viewToHide = null
    }

    fun reset() {
        updateBottomPadding(0)
    }

    private fun updateBottomPadding(value: Int) {
        if (shouldAnimate) {
            animateBottomPaddingTo(value)
        } else {
            delegateView.setPadding(0, 0, 0, value)
        }
    }

    private fun animateBottomPaddingTo(value: Int) {
        isAnimating = true

        val animator = ValueAnimator.ofInt(delegateView.paddingBottom, value)
        animator.addUpdateListener { animation ->
            delegateView.setPadding(0, 0, 0, animation.animatedValue as Int)
        }
        animator.duration = 200
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
            }
        })
        animator.start()
    }

    private fun calculateDifferenceBetweenHeightAndUsableArea(): Int {
        if (decorView == null) {
            decorView = delegateView.rootView
        }

        decorView!!.getWindowVisibleDisplayFrame(rect)

        return delegateView.resources.displayMetrics.heightPixels - rect.bottom
    }
}
