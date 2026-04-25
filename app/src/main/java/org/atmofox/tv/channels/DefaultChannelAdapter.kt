/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.channels

import android.animation.AnimatorInflater
import android.animation.StateListAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.atmofox.tv.R
import org.atmofox.tv.channels.pinnedtile.PinnedTilePlaceholderGenerator

val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChannelTile>() {
    override fun areItemsTheSame(oldTile: ChannelTile, newTile: ChannelTile): Boolean {
        return oldTile.url == newTile.url &&
                oldTile.title == newTile.title
    }

    override fun areContentsTheSame(oldTile: ChannelTile, newTile: ChannelTile): Boolean {
        return oldTile.url == newTile.url &&
                oldTile.title == newTile.title
    }
}

class DefaultChannelAdapter(
    private val context: Context,
    private val loadUrl: (String) -> Unit,
    private val onTileFocused: (() -> Unit)?,
    private val channelConfig: ChannelConfig
) : ListAdapter<ChannelTile, DefaultChannelTileViewHolder>(DIFF_CALLBACK) {

    private val _removeEvents = MutableSharedFlow<ChannelTile>(extraBufferCapacity = 1)
    val removeEvents: Flow<ChannelTile> = _removeEvents.asSharedFlow()

    private val _focusChangeObservable = MutableSharedFlow<Pair<Int, Boolean>>(extraBufferCapacity = 1)
    /**
     * Emits upon focus change events.  Sends Pair of tile index to focusGained
     */
    val focusChangeObservable: Flow<Pair<Int, Boolean>> = _focusChangeObservable.asSharedFlow()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DefaultChannelTileViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.home_tile, parent, false)
        return DefaultChannelTileViewHolder(view)
    }

    override fun onBindViewHolder(holder: DefaultChannelTileViewHolder, position: Int) {
        with(holder) {
            // For carousel scrolling margin updates; For the initial pre-focus state. This
            // doesn't unfortunately handle tile removal - which is handled in
            // [ChannelLayoutManager.onRequestChildFocus()]
            ChannelTile.setChannelMarginByPosition(holder.itemView, context, position, itemCount)
            val tile = getItem(position)
            tile.setImage.invoke(imageView)

            titleView.text = tile.title

            itemView.setOnClickListener {
                loadUrl(tile.url)
                channelConfig.onClickTelemetry?.invoke(tile)
            }

            if (channelConfig.itemsMayBeRemoved) {
                setRemoveOnLongClickListener(itemView, tile)
            }

            itemView.setOnFocusChangeListener { _, hasFocus ->
                // We can't use a selector for the tile cardview because we use the focused item to
                // get the RecyclerView adapter position
                val focusRingDrawable: Drawable?
                val animation: StateListAnimator
                if (hasFocus) {
                    focusRingDrawable = context.getDrawable(R.drawable.tile_selected_stroke)
                    animation = AnimatorInflater.loadStateListAnimator(context, R.animator.channel_item_animator_focused)
                    onTileFocused?.invoke()
                } else {
                    focusRingDrawable = null
                    animation = AnimatorInflater.loadStateListAnimator(context, R.animator.channel_item_animator_not_focused)
                }
                val channelCardView: View = itemView.findViewById(R.id.channel_cardview)
                channelCardView.stateListAnimator = animation
                channelCardView.foreground = focusRingDrawable

                // Slight rotation for custom tiles (sites not in the bundled list)
                if (tile.tileSource == TileSource.CUSTOM) {
                    channelCardView.rotation = PinnedTilePlaceholderGenerator.rotationForUrl(tile.url)
                } else {
                    channelCardView.rotation = 0f
                }

                _focusChangeObservable.tryEmit(position to hasFocus)
                channelConfig.onFocusTelemetry?.invoke(tile, hasFocus)
            }
        }
    }

    private fun setRemoveOnLongClickListener(itemView: View, tile: ChannelTile) {
        itemView.setOnLongClickListener {
            channelConfig.onLongClickTelemetry?.invoke(tile)
            val dialog = Dialog(context, R.style.DialogStyle)
            dialog.setContentView(R.layout.dialog_channel_tiles)
            dialog.window?.setDimAmount(0.85f)
            val titleText: TextView = dialog.findViewById(R.id.titleText)
            val removeTileButton: Button = dialog.findViewById(R.id.removeTileButton)
            val cancelButton: Button = dialog.findViewById(R.id.cancelButton)

            titleText.text = tile.generateRemoveTileTitleStr(context)
            removeTileButton.setOnClickListener {
                _removeEvents.tryEmit(tile)
                dialog.dismiss()
            }

            cancelButton.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()

            true
        }
    }
}

class DefaultChannelTileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val titleView: TextView = itemView.findViewById(R.id.tile_title)
    val imageView: ImageView = itemView.findViewById(R.id.tile_icon)
}
