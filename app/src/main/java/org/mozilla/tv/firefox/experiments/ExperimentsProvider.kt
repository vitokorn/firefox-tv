/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.tv.firefox.experiments

import android.content.Context
import org.mozilla.tv.firefox.R

// service-fretboard removed in mozilla-components 128.x
// This is a stub implementation. TODO: Replace with Nimbus experiments framework.

class ExperimentsProvider(private val fretboard: Any?, private val context: Context) {

    fun getAAExitButtonExperiment(expConfig: ExperimentConfig): String {
        return context.resources.getString(R.string.exit_firefox_a11y,
            context.resources.getString(R.string.firefox_tv_brand_name_short))
    }

    fun shouldShowHintBar(): Boolean = false

    fun shouldShowTvGuideChannels(): Boolean = false

    /**
     * This is not an experiment: see [ExperimentConfig.SEND_TAB] for details.
     *
     * TODO: Remove me! #2855
     */
    fun shouldShowSendTab(): Boolean {
        return true
    }

    /** This is not an experiment: see [ExperimentConfig.MP4_VIDEO_WORKAROUND] for details. */
    fun shouldUseMp4VideoWorkaround(): Boolean = false

    private fun shouldUseTurboRebrand(): Boolean = false

    data class TurboModeToolbarContent(
        val imageId: Int,
        val enabledTextId: Int,
        val disabledTextId: Int
    )

    fun getTurboModeToolbar() = when (shouldUseTurboRebrand()) {
        true -> TurboModeToolbarContent(
            imageId = R.drawable.etp_selector,
            enabledTextId = R.string.toolbar_etp_on,
            disabledTextId = R.string.toolbar_etp_off
        )
        false -> TurboModeToolbarContent(
            imageId = R.drawable.turbo_selector,
            enabledTextId = R.string.turbo_mode,
            disabledTextId = R.string.turbo_mode
        )
    }

    data class TurboModeOnboardingContent(
        val titleId: Int,
        val descriptionId: Int,
        val enableButtonTextId: Int,
        val disableButtonTextId: Int,
        val imageId: Int,
        val imageContentDescriptionId: Int
    )

    fun getTurboModeOnboarding() = when (shouldUseTurboRebrand()) {
        true -> TurboModeOnboardingContent(
            titleId = R.string.onboarding_etp_title,
            descriptionId = R.string.onboarding_etp_description,
            enableButtonTextId = R.string.onboarding_etp_enable,
            disableButtonTextId = R.string.onboarding_etp_disable2,
            imageId = R.drawable.etp_onboarding,
            imageContentDescriptionId = R.string.onboarding_etp_image_a11y
        )
        false -> TurboModeOnboardingContent(
            titleId = R.string.onboarding_turbo_mode_title,
            descriptionId = R.string.onboarding_turbo_mode_body2,
            enableButtonTextId = R.string.button_turbo_mode_keep_enabled2,
            disableButtonTextId = R.string.button_turbo_mode_turn_off2,
            imageId = R.drawable.turbo_mode_onboarding,
            imageContentDescriptionId = R.string.turbo_mode_image_a11y
        )
    }

    // Experiment framework removed in 128.x. All experiments default to control.
    private fun checkBranchVariants(expConfig: ExperimentConfig): Nothing? = null
}
