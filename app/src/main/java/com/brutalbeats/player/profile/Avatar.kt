package com.brutalbeats.player.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.GlassPanel
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.LocalGlassState
import com.brutalbeats.player.ui.components.brutalPress
import com.brutalbeats.player.ui.components.hardShadow
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.ui.theme.Palette
import java.io.File
import java.util.Calendar
import kotlin.math.abs

/** "GOOD EVENING, NAME" by time of day. Just "Good evening" when no name is set. */
fun greetingFor(hour: Int, name: String): String {
    val part = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    return if (name.isBlank()) part else "$part, ${name.trim()}"
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    BrutalHeading(
        text = greetingFor(hour, name),
        modifier = modifier,
        style = BrutalType.h2,
        maxLines = 2
    )
}

private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }

/**
 * Round profile picture: thick black border, hard shadow. Shows the picture, or initials on a flat
 * color block until one is set. [showEditBadge] adds the small glass pencil badge on the corner.
 * Pass [onClick] to make it tappable (press-down animation included).
 */
@Composable
fun Avatar(
    name: String,
    avatarPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showEditBadge: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val c = Brutal.colors
    val fill = if (name.isBlank()) c.accent2 else Palette.AccentPresets[abs(name.hashCode()) % 5]
    val description = if (name.isBlank()) "Profile" else "Profile: $name"

    Box(modifier.size(size)) {
        val circle = Modifier
            .fillMaxSize()
            .semantics { this.contentDescription = description }
            .let { base ->
                if (onClick != null) {
                    base.brutalPress(onClick, CircleShape, 4.dp, c.ink, hapticStrong = true)
                } else {
                    base.hardShadow(4.dp, c.ink, CircleShape)
                }
            }
            .clip(CircleShape)
            .background(fill)
            .border(Brutal.dims.border, c.ink, CircleShape)

        Box(circle, contentAlignment = Alignment.Center) {
            if (avatarPath != null) {
                AsyncImage(
                    model = File(avatarPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (name.isNotBlank()) {
                Text(
                    text = initialsOf(name),
                    style = BrutalType.h2.copy(fontSize = (size.value * 0.38f).sp),
                    color = androidx.compose.ui.graphics.Color.Black
                )
            } else {
                BrutalIcon(Glyph.Music, tint = androidx.compose.ui.graphics.Color.Black, size = size * 0.5f)
            }
        }

        if (showEditBadge) {
            val badge = (size * 0.42f).coerceAtLeast(24.dp)
            // The badge sits inside the blurred screen content, so it uses the glass look without the backdrop blur.
            CompositionLocalProvider(LocalGlassState provides null) {
                GlassPanel(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(badge),
                    shape = CircleShape,
                    shadow = 2.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        BrutalIcon(Glyph.Edit, size = 12.dp)
                    }
                }
            }
        }
    }
}
