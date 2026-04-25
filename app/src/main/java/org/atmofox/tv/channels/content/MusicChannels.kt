/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.channels.content

import org.atmofox.tv.R
import org.atmofox.tv.channels.ChannelTile
import org.atmofox.tv.channels.TileSource

fun ChannelContent.getMusicChannels(): List<ChannelTile> = listOf(
    ChannelTile(
        url = "https://www.npr.org/stations/",
        title = "NPR",
        subtitle = null,
        setImage = setImage(R.drawable.tile_music_npr),
        tileSource = TileSource.MUSIC,
        id = "nprStations"
    ),
    ChannelTile(
        url = "https://bandcamp.com/#discover",
        title = "Bandcamp",
        subtitle = null,
        setImage = setImage(R.drawable.tile_music_bandcamp),
        tileSource = TileSource.MUSIC,
        id = "bandcamp"
    )
)
