/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.channels.content

import org.atmofox.tv.R
import org.atmofox.tv.channels.ChannelTile
import org.atmofox.tv.channels.TileSource

fun ChannelContent.getSportsChannels(): List<ChannelTile> = listOf(
    ChannelTile(
        url = "https://www.nbcsports.com/video",
        title = "NBC Sports",
        subtitle = null,
        setImage = setImage(R.drawable.tile_sports_nbc_sports),
        tileSource = TileSource.SPORTS,
        id = "nbcSports"
    ),
    ChannelTile(
        url = "https://www.formula1.com/en/video.html",
        title = "Formula 1",
        subtitle = null,
        setImage = setImage(R.drawable.tile_sports_formula_1),
        tileSource = TileSource.SPORTS,
        id = "formula1"
    )
)
