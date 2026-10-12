package com.catmap.dubai.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.catmap.dubai.BuildConfig
import com.catmap.dubai.R
import com.catmap.dubai.ui.components.LocalContainer
import com.catmap.dubai.ui.theme.BrandGradient
import kotlinx.coroutines.launch

/** Checks GitHub quietly after sign-in and offers a newer version when there is one. */
@Composable
fun UpdatePrompt() {
    val container = LocalContainer.current
    var release by remember { mutableStateOf<Release?>(null) }
    LaunchedEffect(Unit) { release = container.updater.checkIfDue() }
    release?.let { r -> UpdateSheet(r, onDismiss = { release = null }) }
}

/** The update sheet: what's new, then download with progress, then Android's install screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(release: Release, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val updater = container.updater
    val scope = rememberCoroutineScope()
    val state by updater.download.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = { if (state !is DownloadState.Downloading) { updater.reset(); onDismiss() } }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
                    Text("🚀", style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(stringResource(R.string.update_available), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.update_versions, BuildConfig.VERSION_NAME, release.version), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.whats_new), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).verticalScroll(rememberScrollState()).padding(14.dp)) {
                Text(release.notes.ifBlank { release.title.ifBlank { stringResource(R.string.no_release_notes) } }.replace("**", "").replace("## ", ""), style = MaterialTheme.typography.bodyMedium)
            }
            AnimatedContent(state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "update") { s ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (s) {
                        is DownloadState.Downloading -> {
                            LinearProgressIndicator(progress = { s.progress }, Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
                            Text(stringResource(R.string.downloading_x, (s.progress * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                        }
                        is DownloadState.Ready -> {
                            if (updater.needsInstallPermission()) {
                                Text(stringResource(R.string.install_permission_hint), style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { updater.openInstallPermissionSettings() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.allow_installs)) }
                            }
                            Button(onClick = { updater.install(s.file) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.install_update)) }
                        }
                        DownloadState.Failed -> {
                            Text(stringResource(R.string.update_failed), color = MaterialTheme.colorScheme.error)
                            Button(onClick = { scope.launch { updater.fetch(release) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.try_again)) }
                        }
                        DownloadState.Idle -> {
                            Button(onClick = { scope.launch { updater.fetch(release) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_now)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.later)) }
                                TextButton(onClick = { scope.launch { updater.skip(release.version); onDismiss() } }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.skip_version), color = Color.Unspecified)
                                }
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.update_safety_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
