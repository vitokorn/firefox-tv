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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.atmofox.tv.R
import org.atmofox.tv.compose.navigation.SettingsType
import org.atmofox.tv.channels.ChannelTile
import org.atmofox.tv.channels.ImageSetStrategy
import org.atmofox.tv.channels.SettingsButton
import org.atmofox.tv.channels.SettingsScreen
import org.atmofox.tv.compose.browser.NavigationControls
import org.atmofox.tv.compose.browser.UrlBar
import org.atmofox.tv.compose.theme.Ink80
import org.atmofox.tv.compose.theme.PhotonBlue50
import org.atmofox.tv.compose.theme.PhotonGrey10
import org.atmofox.tv.compose.theme.PhotonGrey50
import org.atmofox.tv.compose.theme.PhotonGrey70
import org.atmofox.tv.compose.theme.TvGray2
import org.atmofox.tv.ext.isVoiceViewEnabled
import org.atmofox.tv.ext.serviceLocator
import org.atmofox.tv.fxa.FxaRepo
import org.atmofox.tv.hint.HintContent
import org.atmofox.tv.hint.OverlayHintViewModel
import org.atmofox.tv.telemetry.MenuInteractionMonitor
import org.atmofox.tv.telemetry.TelemetryIntegration
import org.atmofox.tv.utils.URLs
import org.atmofox.tv.utils.ViewUtils

/**
 * Full-screen menu overlay matching the legacy NavigationOverlayFragment layout.
 *
 * PhotonGrey70 background, top nav buttons, URL bar, channel tiles area,
 * settings tiles, and hint bar at the bottom — all with legacy margins.
 */
private const val SHOW_UNPIN_TOAST_COUNTER_PREF = "show_upin_toast_counter"
private const val MAX_UNPIN_TOAST_COUNT = 3

@Composable
fun MenuOverlay(
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: (SettingsType) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateToUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceLocator = context.serviceLocator
    val channelRepo = serviceLocator.channelRepo
    val fxaRepo = serviceLocator.fxaRepo

    val loadUrlAndNavigateToBrowser: (String) -> Unit = { url ->
        onNavigateToUrl(url)
    }

    val pinnedTiles by channelRepo.pinnedTilesFlow.collectAsState()
    val newsTiles by channelRepo.newsTilesFlow.collectAsState()
    val sportsTiles by channelRepo.sportsTilesFlow.collectAsState()
    val musicTiles by channelRepo.musicTilesFlow.collectAsState()

    val accountState by fxaRepo.accountState.collectAsState()

    val scrollState = rememberScrollState()

    // Dynamic hint bar
    val hintViewModel = remember {
        val closeMenuHint = HintContent(
            text = context.getString(R.string.hint_press_back_to_close_overlay),
            contentDescription = context.getString(R.string.hint_press_back_to_close_overlay_a11y),
            icon = R.drawable.hardware_remote_back
        )
        OverlayHintViewModel(serviceLocator.sessionRepo, closeMenuHint)
    }

    DisposableEffect(Unit) {
        MenuInteractionMonitor.menuOpened()
        onDispose {
            MenuInteractionMonitor.menuClosed()
        }
    }

    Column(
            modifier = modifier
                .fillMaxSize()
                .background(PhotonGrey70)
                .verticalScroll(scrollState)
                .padding(top = 27.dp)
        ) {
            // Top nav buttons with FxA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 57.dp, end = 57.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationControls(
                    onOpenMenu = onNavigateToBrowser,
                    observeBrowserState = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // URL bar (dark rounded rect with search icon)
            UrlBar(
                onSubmit = onNavigateToBrowser,
                onSubmitUrl = loadUrlAndNavigateToBrowser,
                observeBrowserState = true,
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
                    },
                    onTileLongClick = {
                        channelRepo.removeChannelContent(it)
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
                    },
                    onTileLongClick = {
                        channelRepo.removeChannelContent(it)
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
                    },
                    onTileLongClick = {
                        channelRepo.removeChannelContent(it)
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
                    },
                    onTileLongClick = {
                        channelRepo.removeChannelContent(it)
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

            // Settings tiles row with telemetry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 45.dp, end = 57.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsTile(
                    label = context.getString(R.string.menu_settings),
                    iconRes = R.drawable.ic_data_collection,
                    onClick = {
                        TelemetryIntegration.INSTANCE.settingsTileClickEvent(SettingsScreen.DATA_COLLECTION)
                        onNavigateToSettings(SettingsType.COMMON)
                    }
                )
                SettingsTile(
                    label = context.getString(R.string.menu_about),
                    iconRes = R.drawable.mozac_ic_info,
                    onClick = {
                        TelemetryIntegration.INSTANCE.settingsTileClickEvent(SettingsScreen.ABOUT)
                        onNavigateToSettings(SettingsType.ABOUT)
                    }
                )
                SettingsTile(
                    label = context.getString(R.string.preference_privacy_notice),
                    iconRes = R.drawable.mozac_ic_globe,
                    onClick = {
                        TelemetryIntegration.INSTANCE.settingsTileClickEvent(SettingsButton.PRIVACY_POLICY)
                        loadUrlAndNavigateToBrowser(URLs.PRIVACY_NOTICE_URL)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic hint bar
            DynamicHintBar(viewModel = hintViewModel)
        }
}

@Composable
private fun ChannelRow(
    title: String,
    tiles: List<ChannelTile>,
    tileWidth: androidx.compose.ui.unit.Dp,
    tileHeight: androidx.compose.ui.unit.Dp,
    onTileClick: (ChannelTile) -> Unit,
    onTileLongClick: (ChannelTile) -> Unit,
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
                    onClick = { onTileClick(tile) },
                    onLongClick = { onTileLongClick(tile) }
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var canShowUnpinToast by remember { mutableStateOf(true) }

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
            .onFocusChanged { focusState ->
                if (focusState.isFocused && canShowUnpinToast) {
                    val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    val prefInt = prefs.getInt(SHOW_UNPIN_TOAST_COUNTER_PREF, 0)
                    if (prefInt < MAX_UNPIN_TOAST_COUNT) {
                        prefs.edit().putInt(SHOW_UNPIN_TOAST_COUNTER_PREF, prefInt + 1).apply()
                        val contextRef = java.lang.ref.WeakReference(context)
                        val showToast = {
                            val ctx = contextRef.get()
                            if (ctx != null) {
                                ViewUtils.showCenteredBottomToast(ctx, R.string.homescreen_unpin_tutorial_toast)
                            }
                        }
                        if (context.isVoiceViewEnabled()) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(showToast, 1500)
                        } else {
                            showToast()
                        }
                        canShowUnpinToast = false
                    }
                }
            }
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            )
            .onKeyEvent { event ->
                if (event.key == androidx.compose.ui.input.key.Key.DirectionCenter || event.key == androidx.compose.ui.input.key.Key.Enter) {
                    when (event.type) {
                        androidx.compose.ui.input.key.KeyEventType.KeyDown -> {
                            longPressJob?.cancel()
                            longPressJob = scope.launch {
                                delay(500)
                                onLongClick()
                                longPressJob = null
                            }
                            true
                        }
                        androidx.compose.ui.input.key.KeyEventType.KeyUp -> {
                            longPressJob?.cancel()
                            longPressJob = null
                            false
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
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
private fun FxaAccountButton(
    accountState: FxaRepo.AccountState,
    onLoginClick: () -> Unit,
    onProfileClick: () -> Unit,
    onReauthenticateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val (iconRes, contentDescription, onClick) = when (accountState) {
        is FxaRepo.AccountState.AuthenticatedWithProfile -> {
            val profile = accountState.profile
            Triple(
                R.drawable.ic_default_avatar,
                profile.displayName,
                onProfileClick
            )
        }
        is FxaRepo.AccountState.AuthenticatedNoProfile -> {
            Triple(
                R.drawable.ic_avatar_authenticated_no_picture,
                "Signed in",
                onProfileClick
            )
        }
        is FxaRepo.AccountState.NeedsReauthentication -> {
            Triple(
                R.drawable.ic_fxa_needs_reauthentication,
                "Sign in again",
                onReauthenticateClick
            )
        }
        is FxaRepo.AccountState.NotAuthenticated,
        is FxaRepo.AccountState.Initial -> {
            Triple(
                R.drawable.ic_fxa_login,
                "Sign in",
                onLoginClick
            )
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(if (isFocused) 1.15f else 1f)
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(36.dp),
            tint = PhotonGrey10
        )
    }
}

@Composable
private fun DynamicHintBar(
    viewModel: OverlayHintViewModel,
    modifier: Modifier = Modifier
) {
    val isDisplayed by viewModel.isDisplayed.collectAsState(initial = false)
    val hints by viewModel.hints.collectAsState(initial = emptyList())

    AnimatedVisibility(
        visible = isDisplayed,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(PhotonGrey70)
                .padding(start = 48.dp, end = 48.dp, top = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            val hint = hints.firstOrNull()
            if (hint != null) {
                Text(
                    text = hint.text,
                    fontSize = 18.sp,
                    color = TvGray2
                )
            }
        }
    }
}
