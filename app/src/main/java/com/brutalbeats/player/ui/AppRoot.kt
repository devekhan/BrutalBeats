package com.brutalbeats.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.data.prefs.ThemeMode
import com.brutalbeats.player.permissions.AudioPermissionScreen
import com.brutalbeats.player.permissions.findActivity
import com.brutalbeats.player.permissions.rememberAppPermissions
import com.brutalbeats.player.ui.components.LoadingState
import com.brutalbeats.player.ui.components.LocalGlassState
import com.brutalbeats.player.ui.components.glassSource
import com.brutalbeats.player.ui.components.rememberGlassState
import com.brutalbeats.player.ui.nav.AppSheets
import com.brutalbeats.player.ui.nav.BottomNav
import com.brutalbeats.player.ui.nav.LocalBottomInset
import com.brutalbeats.player.ui.nav.LocalSheets
import com.brutalbeats.player.ui.nav.Navigator
import com.brutalbeats.player.ui.nav.Page
import com.brutalbeats.player.ui.nav.SheetHost
import com.brutalbeats.player.ui.nav.TopTab
import com.brutalbeats.player.ui.screens.equalizer.EqualizerScreen
import com.brutalbeats.player.ui.screens.home.HomeScreen
import com.brutalbeats.player.ui.screens.library.DetailScreen
import com.brutalbeats.player.ui.screens.library.LibraryScreen
import com.brutalbeats.player.ui.screens.player.MiniPlayer
import com.brutalbeats.player.ui.screens.player.NowPlayingScreen
import com.brutalbeats.player.ui.screens.player.PlayerErrorBanner
import com.brutalbeats.player.ui.screens.player.PlayerViewModel
import com.brutalbeats.player.ui.screens.settings.AboutScreen
import com.brutalbeats.player.ui.screens.settings.FolderRulesScreen
import com.brutalbeats.player.ui.screens.settings.SettingsScreen
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalTheme
import com.brutalbeats.player.ui.theme.LocalGlassIntensity

/** Picks the theme from your settings, then draws the app. */
@Composable
fun BrutalBeatsApp(vm: AppViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize()) // one frame while settings load
    } else {
        val systemDark = isSystemInDarkTheme()
        val dark = when (s.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        BrutalTheme(
            darkTheme = dark,
            highContrast = s.highContrast,
            accent = Color(s.accentArgb),
            glassIntensity = s.glassIntensity
        ) {
            val view = LocalView.current
            SideEffect {
                val window = view.context.findActivity()?.window
                if (window != null) {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
            }
            AppRoot(vm)
        }
    }
}

@Composable
private fun AppRoot(
    appVm: AppViewModel,
    playerVm: PlayerViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val perms = rememberAppPermissions()
    val sheets = remember { AppSheets() }
    val navigator = remember { Navigator() }
    val glass = rememberGlassState()
    val intensity = LocalGlassIntensity.current
    val song by playerVm.song.collectAsStateWithLifecycle()
    val firstScan by appVm.firstScan.collectAsStateWithLifecycle()
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }

    // Ask for audio (+ notification) permission on launch, and scan once audio is allowed.
    LaunchedEffect(Unit) {
        if (!perms.audio || !perms.notifications) perms.requestStartup()
    }
    LaunchedEffect(perms.audio) {
        if (perms.audio) appVm.scanOnStart()
    }

    // Space screens leave at the bottom for the bottom bar (+ mini player when a song is loaded).
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomInset = navBarInset + 86.dp + (if (song != null && perms.audio) 88.dp else 0.dp)

    // Back: close a page, else go to Home. (Now Playing and sheets register their own, later, so they win.)
    BackHandler(enabled = navigator.stack.isNotEmpty() || navigator.tab != TopTab.HOME) {
        if (!navigator.pop()) navigator.select(TopTab.HOME)
    }

    CompositionLocalProvider(
        LocalSheets provides sheets,
        LocalGlassState provides glass,
        LocalBottomInset provides bottomInset
    ) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            // 1) The screens. This box is what the glass panels frost.
            Box(Modifier.fillMaxSize().glassSource(glass, intensity)) {
                when {
                    !perms.audio -> Box(
                        Modifier.fillMaxSize().statusBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        AudioPermissionScreen(
                            blocked = perms.audioBlocked,
                            onGrant = perms::requestStartup,
                            onOpenSettings = perms::openSettings
                        )
                    }
                    firstScan -> Box(
                        Modifier.fillMaxSize().statusBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) { LoadingState(label = "Scanning your music") }
                    else -> PageHost(navigator)
                }
            }

            // 2) Mini player + bottom bar float above the screens.
            if (perms.audio) {
                Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                    PlayerErrorBanner()
                    MiniPlayer(onExpand = { nowPlayingOpen = true })
                    if (song != null) Spacer(Modifier.height(8.dp))
                    BottomNav(
                        current = navigator.tab,
                        onSelect = { navigator.select(it) }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            // 3) Now Playing slides over everything.
            NowPlayingScreen(
                visible = nowPlayingOpen,
                onClose = { nowPlayingOpen = false },
                onOpenEqualizer = {
                    nowPlayingOpen = false
                    navigator.select(TopTab.EQUALIZER)
                }
            )

            // 4) Sheets sit on top (Now Playing draws its own copy while it is open).
            if (!nowPlayingOpen) SheetHost()
        }
    }
}

@Composable
private fun PageHost(navigator: Navigator) {
    val current: Any = navigator.stack.lastOrNull() ?: navigator.tab
    AnimatedContent(
        targetState = current,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
        label = "pages"
    ) { target ->
        when (target) {
            is TopTab -> when (target) {
                TopTab.HOME -> HomeScreen(onOpenLibrary = { navigator.select(TopTab.LIBRARY) })
                TopTab.LIBRARY -> LibraryScreen(onOpenDetail = { navigator.push(Page.Songs(it)) })
                TopTab.EQUALIZER -> EqualizerScreen()
                TopTab.SETTINGS -> SettingsScreen(
                    onOpenFolders = { navigator.push(Page.FolderRules) },
                    onOpenAbout = { navigator.push(Page.About) }
                )
            }
            is Page.Songs -> DetailScreen(detail = target.detail, onBack = { navigator.pop() })
            is Page.FolderRules -> FolderRulesScreen(onBack = { navigator.pop() })
            is Page.About -> AboutScreen(onBack = { navigator.pop() })
            else -> Unit
        }
    }
}
