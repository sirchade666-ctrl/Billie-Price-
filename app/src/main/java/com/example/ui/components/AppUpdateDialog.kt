package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppReleaseInfo
import com.example.data.model.AppUpdateUiState
import com.example.data.model.UpdateCheckStatus
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack

/**
 * Creator Program-compliant Update Available pop-up dialog.
 * Displays release notes and opens the external release/store download page via
 * a zero-permission ACTION_VIEW intent without downloading or installing APKs in-app.
 */
@Composable
fun AppUpdateAvailableDialog(
    currentVersionName: String,
    release: AppReleaseInfo,
    onOpenDownloadPage: (String) -> Unit,
    onSkipVersion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("app_update_dialog"),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = PhosphorCyan,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PhosphorCyan.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "v$currentVersionName  →  v${release.versionName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PhosphorCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("update_version_badge")
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = release.releaseTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (release.publishedAt.isNotBlank()) {
                    Text(
                        text = "Released ${release.publishedAt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Text(
                    text = release.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "Tapping Download Update opens the official release or store page in your browser or Play Store to install safely.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GeoportTeal
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onOpenDownloadPage(release.downloadPageUrl) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhosphorCyan,
                    contentColor = VoidBlack
                ),
                modifier = Modifier.testTag("update_open_download_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download Update", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { onSkipVersion(release.versionName) },
                    modifier = Modifier.testTag("update_skip_button")
                ) {
                    Text("Skip", color = TextMuted)
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("update_later_button")
                ) {
                    Text("Later")
                }
            }
        }
    )
}

/**
 * Update Center dialog allowing investigators to configure their GitHub repository
 * or store link, check for new Geoport EMF releases, and toggle startup update checks.
 */
@Composable
fun AppUpdateCenterDialog(
    updateState: AppUpdateUiState,
    onSaveSourceAndCheck: (String) -> Unit,
    onAutoCheckToggle: (Boolean) -> Unit,
    onOpenExternalPage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceInput by remember(updateState.updateSource) {
        mutableStateOf(updateState.updateSource)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("update_settings_dialog"),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = PhosphorCyan,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "App Updates",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Installed Version",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "Geoport EMF v${updateState.currentVersionName} (Build ${updateState.currentVersionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("installed_version_label")
                        )
                    }
                    if (updateState.status == UpdateCheckStatus.UP_TO_DATE) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = GeoportTeal
                        )
                    }
                }

                OutlinedTextField(
                    value = sourceInput,
                    onValueChange = { sourceInput = it },
                    label = { Text("GitHub Repo or Release URL") },
                    placeholder = { Text("e.g. username/geoport-emf") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_source_input")
                )

                Text(
                    text = "Enter your GitHub repository (owner/repo) so the app automatically checks GitHub Releases and pops up when a new version is published.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-Check on App Launch",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = updateState.autoCheckOnLaunch,
                        onCheckedChange = onAutoCheckToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VoidBlack,
                            checkedTrackColor = PhosphorCyan
                        ),
                        modifier = Modifier.testTag("update_auto_check_switch")
                    )
                }

                if (updateState.status == UpdateCheckStatus.CHECKING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = PhosphorCyan
                        )
                        Text(
                            text = "Checking for latest release...",
                            style = MaterialTheme.typography.bodySmall,
                            color = PhosphorCyan
                        )
                    }
                } else if (updateState.statusMessage.isNotBlank()) {
                    val msgColor = when (updateState.status) {
                        UpdateCheckStatus.UPDATE_AVAILABLE -> PhosphorCyan
                        UpdateCheckStatus.UP_TO_DATE -> GeoportTeal
                        UpdateCheckStatus.ERROR -> FranksAmber
                        else -> TextSecondary
                    }
                    Text(
                        text = updateState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = msgColor,
                        modifier = Modifier.testTag("update_status_message")
                    )
                }

                FilledTonalButton(
                    onClick = { onOpenExternalPage(sourceInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_store_page_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Releases / Play Store Page")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveSourceAndCheck(sourceInput) },
                enabled = updateState.status != UpdateCheckStatus.CHECKING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PhosphorCyan,
                    contentColor = VoidBlack
                ),
                modifier = Modifier.testTag("check_updates_now_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Check Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
