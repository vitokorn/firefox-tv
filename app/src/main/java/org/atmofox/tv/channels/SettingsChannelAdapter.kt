/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.channels

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import org.atmofox.tv.R
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.utils.URLs

class SettingsChannelAdapter(
    private val loadUrl: (String) -> Unit,
    private val showSettings: (SettingsScreen) -> Unit
) : RecyclerView.Adapter<SettingsTileHolder>() {
    private val settingsItems = arrayOf(
        SettingsItem(
            SettingsScreen.DATA_COLLECTION,
            R.drawable.ic_data_collection,
            R.string.preference_mozilla_telemetry2,
            R.id.settings_tile_telemetry),
        SettingsItem(
            SettingsScreen.CLEAR_COOKIES,
            R.drawable.mozac_ic_delete,
            R.string.settings_cookies_dialog_title,
            R.id.settings_tile_cleardata),
        SettingsItem(
            SettingsScreen.ABOUT,
            R.drawable.mozac_ic_info,
            R.string.menu_about,
            R.id.settings_tile_about),
        SettingsItem(
            SettingsButton.PRIVACY_POLICY,
            R.drawable.mozac_ic_globe,
            R.string.preference_privacy_notice,
            R.id.settings_tile_privacypolicy)
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SettingsTileHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.settings_tile, parent, false)
    )

    override fun getItemCount(): Int {
        return settingsItems.size
    }

    override fun onBindViewHolder(holder: SettingsTileHolder, position: Int) = with(holder) {
        val itemData = settingsItems[position]
        iconView.setImageResource(itemData.imgRes)
        titleView.setText(itemData.titleRes)
        cardView.setOnClickListener {
            when (val type = itemData.type) {
                is SettingsScreen -> showSettings(type)
                SettingsButton.PRIVACY_POLICY -> loadUrl(URLs.PRIVACY_NOTICE_URL)
            }
            TelemetryIntegration.INSTANCE.settingsTileClickEvent(itemData.type)
        }
        itemView.contentDescription = itemView.context.getString(itemData.titleRes)
        itemView.id = itemData.viewId // Add ids for testing
    }
}

class SettingsTileHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val cardView: CardView = itemView.findViewById(R.id.settings_cardview)
    val iconView: ImageView = itemView.findViewById(R.id.settings_icon)
    val titleView: TextView = itemView.findViewById(R.id.settings_title)
}

// We differentiate between Settings tiles that lead to other Settings screens, or are just buttons
interface SettingsTile
enum class SettingsScreen : SettingsTile {
    DATA_COLLECTION, CLEAR_COOKIES, FXA_PROFILE, ABOUT
}
enum class SettingsButton : SettingsTile {
        PRIVACY_POLICY
}

private data class SettingsItem(val type: SettingsTile, val imgRes: Int, val titleRes: Int, val viewId: Int)
