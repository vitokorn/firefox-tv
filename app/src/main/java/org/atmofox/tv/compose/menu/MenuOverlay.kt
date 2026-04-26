/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.atmofox.tv.compose.menu

import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.atmofox.tv.R
import org.atmofox.tv.compose.navigation.SettingsType
import org.atmofox.tv.channels.ChannelTile
import org.atmofox.tv.channels.ImageSetStrategy
import org.atmofox.tv.compose.browser.NavigationControls
import org.atmofox.tv.compose.browser.UrlBar
import org.atmofox.tv.compose.theme.Ink80
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.PhotonGrey10
import org.atmofox.tv.compose.theme.PhotonGrey50
import org.atmofox.tv.compose.theme.PhotonGrey70
import org.atmofox.tv.compose.theme.TvGray2
import androidx.compose.runtime.collectAsState
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.utils.URLs

/**
 * Full-screen menu overlay matching the legacy NavigationOverlayFragment layout.
 *
 * PhotonGrey70 background, top nav buttons, URL bar, channel tiles area,
 * settings tiles, and hint bar at the bottom — all with legacy margins.
 */
@Composable
fun MenuOverlay(
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: (SettingsType) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateToUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val channelRepo = context.serviceLocator.channelRepo

    val loadUrlAndNavigateToBrowser: (String) -> Unit = { url ->
        onNavigateToUrl(url)
    }

    val pinnedTiles by channelRepo.pinnedTilesFlow.collectAsState()
    val newsTiles by channelRepo.newsTilesFlow.collectAsState()
    val sportsTiles by channelRepo.sportsTilesFlow.collectAsState()
    val musicTiles by channelRepo.musicTilesFlow.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PhotonGrey70)
            .verticalScroll(scrollState)
            .padding(top = 27.dp)
    ) {
        // Top nav buttons (same style as browser)
        NavigationControls(
            observeBrowserState = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 57.dp, end = 57.dp, bottom = 24.dp)
        )

        // URL bar (dark rounded rect with search icon)
        UrlBar(
            onSubmit = onNavigateToBrowser,
            onSubmitUrl = loadUrlAndNavigateToBrowser,
            observeBrowserState = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 57.dp, end = 57.dp, bottom = 48.dp)
        )

        // Channel tiles
        val tileHeight = 108.dp
        val tileWidth = 193.5.dp

        if (pinnedTiles.isNotEmpty()) {
            ChannelRow(
                title = context.getString(R.string.pinned_tile_channel_title),
                tiles = pinnedTiles,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                onTileClick = {
                    loadUrlAndNavigateToBrowser(it.url)
                }
            )
        }
        if (newsTiles.isNotEmpty()) {
            ChannelRow(
                title = "News",
                tiles = newsTiles,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                onTileClick = {
                    loadUrlAndNavigateToBrowser(it.url)
                }
            )
        }
        if (sportsTiles.isNotEmpty()) {
            ChannelRow(
                title = "Sports",
                tiles = sportsTiles,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                onTileClick = {
                    loadUrlAndNavigateToBrowser(it.url)
                }
            )
        }
        if (musicTiles.isNotEmpty()) {
            ChannelRow(
                title = "Music",
                tiles = musicTiles,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                onTileClick = {
                    loadUrlAndNavigateToBrowser(it.url)
                }
            )
        }

        // Settings section title
        Text(
            text = "Menu Settings",
            fontSize = 24.sp,
            color = PhotonGrey10,
            modifier = Modifier.padding(start = 57.dp, end = 57.dp, top = 24.dp, bottom = 16.dp)
        )

        // Settings tiles row (matching old SettingsChannelAdapter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 45.dp, end = 57.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val context = LocalContext.current
            SettingsTile(
                label = context.getString(R.string.menu_settings),
                iconRes = R.drawable.ic_data_collection,
                onClick = { onNavigateToSettings(SettingsType.COMMON) }
            )
            SettingsTile(
                label = context.getString(R.string.menu_about),
                iconRes = R.drawable.mozac_ic_info,
                onClick = { onNavigateToSettings(SettingsType.ABOUT) }
            )
            SettingsTile(
                label = context.getString(R.string.preference_privacy_notice),
                iconRes = R.drawable.mozac_ic_globe,
                onClick = {
                    // Privacy policy loads a URL directly, not a settings screen
                    loadUrlAndNavigateToBrowser(URLs.PRIVACY_NOTICE_URL)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Hint bar at the bottom (legacy style)
        HintBar()
    }
}

@Composable
private fun ChannelRow(
    title: String,
    tiles: List<ChannelTile>,
    tileWidth: androidx.compose.ui.unit.Dp,
    tileHeight: androidx.compose.ui.unit.Dp,
    onTileClick: (ChannelTile) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = 57.dp, end = 57.dp, bottom = 16.dp)) {
        Text(
            text = title,
            fontSize = 24.sp,
            color = PhotonGrey10,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            tiles.forEach { tile ->
                ChannelTileItem(
                    tile = tile,
                    width = tileWidth,
                    height = tileHeight,
                    onClick = { onTileClick(tile) }
                )
            }
        }
    }
}

@Composable
private fun ChannelTileItem(
    tile: ChannelTile,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val imageModel = when (tile.setImage) {
        is ImageSetStrategy.ById -> tile.setImage.id
        is ImageSetStrategy.ByPath -> tile.setImage.path
        is ImageSetStrategy.ByFile -> tile.setImage.file
    }

    val imageRequest = ImageRequest.Builder(context)
        .data(imageModel)
        .memoryCacheKey(imageModel.toString())
        .diskCacheKey(imageModel.toString())
        .apply {
            when (tile.setImage) {
                is ImageSetStrategy.ByPath -> {
                    tile.setImage.placeholderId?.let { placeholder(it) }
                    tile.setImage.errorId?.let { error(it) }
                }
                is ImageSetStrategy.ByFile -> {
                    placeholder(tile.setImage.backup)
                    error(tile.setImage.backup)
                }
                else -> {}
            }
        }
        .build()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(width)
            .scale(if (isFocused) 1.08f else 1f)
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
    ) {
        // Image card — fills the full tile dimensions, no padding
        Box(
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(4.dp))
                .background(Ink80)
                .border(
                    width = if (isFocused) 4.dp else 0.dp,
                    color = if (isFocused) PhotonBlue50 else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = tile.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Title below the card, matching old home_tile.xml layout
        Text(
            text = tile.title,
            fontSize = 18.sp,
            color = PhotonGrey10,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SettingsTile(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(193.5.dp)
            .scale(if (isFocused) 1.08f else 1f)
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
    ) {
        // Icon card
        Box(
            modifier = Modifier
                .size(193.5.dp, 108.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PhotonGrey50)
                .border(
                    width = if (isFocused) 4.dp else 0.dp,
                    color = if (isFocused) PhotonBlue50 else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = PhotonGrey10
            )
        }

        // Title below
        Text(
            text = label,
            fontSize = 18.sp,
            color = PhotonGrey10,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun HintBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(PhotonGrey70)
            .padding(start = 48.dp, end = 48.dp, top = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "Use your remote to navigate",
            fontSize = 18.sp,
            color = TvGray2
        )
    }
}
