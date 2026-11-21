package com.brutalbeats.player.profile

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.brutalbeats.player.ui.components.BrutalButton
import com.brutalbeats.player.ui.components.BrutalHeading
import com.brutalbeats.player.ui.components.BrutalIcon
import com.brutalbeats.player.ui.components.BrutalIconButton
import com.brutalbeats.player.ui.components.BrutalSheet
import com.brutalbeats.player.ui.components.BrutalTextField
import com.brutalbeats.player.ui.components.Glyph
import com.brutalbeats.player.ui.theme.Brutal
import com.brutalbeats.player.ui.theme.BrutalType
import kotlinx.coroutines.launch

/**
 * Profile glass sheet: display name, profile picture (Android photo picker, no storage permission),
 * change / remove, and the pan/zoom cropper. Call it from the root Box, next to the other sheets.
 */
@Composable
fun BoxScope.ProfileSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val c = Brutal.colors
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var nameDraft by remember { mutableStateOf("") }
    LaunchedEffect(visible) { if (visible) nameDraft = profile.name }

    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = viewModel.loadForCrop(uri)
                if (bmp == null) error = "Couldn't open that photo. Try another one." else {
                    error = null
                    cropSource = bmp
                }
            }
        }
    }
    val pickPhoto = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    BrutalSheet(visible = visible && cropSource == null, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrutalHeading("Your profile", style = BrutalType.h2, modifier = Modifier.weight(1f))
            BrutalIconButton(
                onClick = onDismiss,
                contentDescription = "Close profile",
                fill = c.surface
            ) { BrutalIcon(Glyph.Close) }
        }
        Spacer(Modifier.height(20.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Avatar(
                name = nameDraft,
                avatarPath = profile.avatarPath,
                size = 104.dp,
                showEditBadge = true,
                onClick = pickPhoto
            )
        }
        Spacer(Modifier.height(24.dp))

        BrutalTextField(
            value = nameDraft,
            onValueChange = { if (it.length <= 24) nameDraft = it },
            placeholder = "Display name",
            modifier = Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)
        )
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrutalButton(
                text = if (profile.avatarPath == null) "Add photo" else "Change photo",
                onClick = pickPhoto,
                fill = c.accent4,
                modifier = Modifier.weight(1f)
            )
            if (profile.avatarPath != null) {
                BrutalButton(
                    text = "Remove",
                    onClick = { viewModel.removeAvatar() },
                    fill = c.surface,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", style = BrutalType.body, color = c.danger)
        }

        Spacer(Modifier.height(18.dp))
        BrutalButton(
            text = "Save",
            onClick = {
                viewModel.setName(nameDraft)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)
        )
    }

    val source = cropSource
    if (visible && source != null) {
        Box(Modifier.matchParentSize()) {
            AvatarCropper(
                bitmap = source,
                onCancel = { cropSource = null },
                onDone = { cropped ->
                    viewModel.saveAvatar(cropped)
                    cropSource = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
