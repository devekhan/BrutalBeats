package com.brutalbeats.player.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.ui.components.BrutalButton
import com.brutalbeats.player.ui.components.BrutalChip
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.EmptyState
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.components.LoadingState
import com.brutalbeats.player.ui.components.brutalBox
import com.brutalbeats.player.ui.nav.LocalBottomInset
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import com.brutalbeats.player.ui.theme.readable

/** Choose which folders the app uses: HIDE a folder, or mark folders ONLY to use just those. */
@Composable
fun FolderRulesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val bottom = LocalBottomInset.current
    val folders by vm.allFolders.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val list = folders
    val shape = RoundedCornerShape(Brutal.dims.corner)

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrutalIconButton(onClick = onBack, contentDescription = "Go back", fill = c.surface) {
                BrutalIcon(Glyph.Back)
            }
            BrutalHeading("Folders", style = BrutalType.h2, modifier = Modifier.weight(1f))
        }
        Text(
            text = "HIDE keeps a folder out of the app (voice notes, call recordings...). " +
                "ONLY limits the app to the folders you mark. Subfolders follow their parent.",
            style = BrutalType.body,
            color = c.textMuted,
            modifier = Modifier.padding(start = 20.dp, end = 24.dp, bottom = 12.dp)
        )
        if (s.excludedFolders.isNotEmpty() || s.includedFolders.isNotEmpty()) {
            Row(Modifier.padding(start = 16.dp, end = 22.dp, bottom = 12.dp)) {
                BrutalButton("Reset all", onClick = vm::resetFolderRules, fill = c.surface)
            }
        }
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState() }
            list.isEmpty() -> EmptyState(
                title = "No folders yet",
                message = "Scan your library first, then your folders show up here.",
                glyph = Glyph.Folder
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottom + 16.dp)
            ) {
                items(list, key = { it.path }) { folder ->
                    val hidden = folder.path in s.excludedFolders
                    val only = folder.path in s.includedFolders
                    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 2.dp, bottom = 8.dp)) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .brutalBox(c.surface, shape, Brutal.dims.border, c.ink, 4.dp)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = folder.name,
                                style = BrutalType.bodyBold,
                                color = c.surface.readable(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${folder.songCount} songs · ${folder.path}",
                                style = BrutalType.caption,
                                color = c.surface.readable().copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BrutalChip(
                                    text = "Hide",
                                    selected = hidden,
                                    onClick = { vm.toggleHidden(folder.path) },
                                    selectedFill = c.danger
                                )
                                BrutalChip(
                                    text = "Only",
                                    selected = only,
                                    onClick = { vm.toggleOnly(folder.path) },
                                    selectedFill = c.accent3
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
