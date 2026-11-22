package com.brutalbeats.player.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brutalbeats.player.BuildConfig
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.BrutalSurface
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.Sticker
import com.brutalbeats.player.ui.nav.LocalBottomInset
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType

/** About page: what the app is, the privacy promise, and font credits. */
@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = Brutal.colors
    val bottom = LocalBottomInset.current

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = bottom + 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrutalIconButton(onClick = onBack, contentDescription = "Go back", fill = c.surface) {
                BrutalIcon(Glyph.Back)
            }
            BrutalHeading("About", style = BrutalType.h2)
        }

        Column(Modifier.padding(start = 20.dp, end = 26.dp)) {
            BrutalSurface(Modifier.fillMaxWidth(), fill = c.accent, rotation = -1f) {
                Column(Modifier.padding(20.dp)) {
                    Text("BRUTAL", style = BrutalType.display, color = c.ink)
                    Text("BEATS", style = BrutalType.display, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    Sticker("Version ${BuildConfig.VERSION_NAME}", fill = c.accent2, rotation = 2f)
                }
            }
            Spacer(Modifier.height(24.dp))

            PromiseCard(
                title = "100% offline",
                text = "This app has no internet permission at all. It physically cannot send anything anywhere.",
                fill = c.accent3
            )
            PromiseCard(
                title = "No account. No ads. No tracking.",
                text = "Your playlists, favorites and play counts live only on this phone.",
                fill = c.accent4
            )
            PromiseCard(
                title = "Fonts",
                text = "Archivo Black and Space Mono, both under the SIL Open Font License, bundled in the app.",
                fill = c.surface
            )
        }
    }
}

@Composable
private fun PromiseCard(title: String, text: String, fill: androidx.compose.ui.graphics.Color) {
    val fg = androidx.compose.ui.graphics.Color.Black.takeIf { fill != Brutal.colors.accent4 }
        ?: androidx.compose.ui.graphics.Color.White
    val textColor = if (fill == Brutal.colors.surface) Brutal.colors.text else fg
    BrutalSurface(Modifier.fillMaxWidth().padding(bottom = 16.dp), fill = fill) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), style = BrutalType.h3, color = textColor)
            Spacer(Modifier.height(6.dp))
            Text(text, style = BrutalType.body, color = textColor)
        }
    }
}
