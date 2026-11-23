package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AudioClipEntity
import com.example.data.local.EmfAnomalyEntity
import com.example.data.local.InvestigationSessionEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.model.EmfReading
import com.example.data.model.LocationStatus
import com.example.ui.components.ActiveSessionBanner
import com.example.ui.components.InstrumentCard
import com.example.ui.components.StatusLed
import com.example.ui.theme.AnomalyCrimson
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    locationStatus: LocationStatus,
    activeSession: InvestigationSessionEntity?,
    currentEmfReading: EmfReading,
    recentAnomalies: List<EmfAnomalyEntity>,
    audioClips: List<AudioClipEntity>,
    journalEntries: List<JournalEntryEntity>,
    onRequestLocationPermission: () -> Unit,
    onStartSessionClick: () -> Unit,
    onStopSessionClick: () -> Unit,
    onSaveJournalEntry: (
        locationLabel: String,
        noteText: String,
        tags: String,
        associatedAnomaly: EmfAnomalyEntity?,
        associatedClip: AudioClipEntity?
    ) -> Unit,
    onPlayClip: (AudioClipEntity) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onExportJournalCsv: () -> Unit,
    onExportJournalJson: () -> Unit,
    onShareJournal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var locationLabel by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    val selectedTags = remember { mutableStateListOf<String>() }
    var selectedAnomaly by remember { mutableStateOf<EmfAnomalyEntity?>(null) }
    var selectedClip by remember { mutableStateOf<AudioClipEntity?>(null) }
    var anomalyMenuExpanded by remember { mutableStateOf(false) }
    var clipMenuExpanded by remember { mutableStateOf(false) }

    val availableTags = listOf("Cold Spot", "Footsteps", "EMF Surge", "EVP", "Voice", "Visual")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ActiveSessionBanner(
                activeSession = activeSession,
                onStartSessionClick = onStartSessionClick,
                onStopSessionClick = onStopSessionClick
            )
        }

        // GPS Coordinate Status Card
        item {
            val coord = locationStatus.currentCoordinate
            InstrumentCard(
                borderColor = if (coord != null) GeoportTeal.copy(alpha = 0.65f) else FranksAmber.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (coord != null) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                            contentDescription = null,
                            tint = if (coord != null) GeoportTeal else FranksAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusLed(
                                    active = locationStatus.isTracking && coord != null,
                                    activeColor = GeoportTeal,
                                    pulse = true
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        !locationStatus.hasPermission -> "GPS PERMISSION REQUIRED"
                                        coord != null -> "GPS LOCK ACTIVE (${coord.provider.uppercase()})"
                                        locationStatus.isTracking -> "ACQUIRING GPS FIX..."
                                        else -> "GPS PROVIDER STANDBY"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (coord != null) GeoportTeal else FranksAmber
                                )
                            }
                            if (coord != null) {
                                Text(
                                    text = "${"%.6f".format(Locale.US, coord.latitude)}, ${"%.6f".format(Locale.US, coord.longitude)}  (±${"%.0f".format(Locale.US, coord.accuracyMeters)}m)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else if (!locationStatus.hasPermission) {
                                Text(
                                    text = stringResource(R.string.permission_location_rationale),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                if (!locationStatus.hasPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestLocationPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FranksAmber,
                            contentColor = VoidBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("grant_location_permission_button")
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.grant_location_permission), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // New Journal Entry Composer
        item {
            InstrumentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = PhosphorCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LOG FIELD OBSERVATION",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = locationLabel,
                    onValueChange = { locationLabel = it },
                    label = { Text("Room / Spot Label") },
                    placeholder = { Text("e.g., 2nd Floor North Stairwell") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("journal_location_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Investigation Note") },
                    placeholder = { Text("Describe phenomena, temperature drop, or responses...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("journal_note_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Phenomenon Tags",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableTags.forEach { tag ->
                        val isSelected = selectedTags.contains(tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag)
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PhosphorCyan.copy(alpha = 0.2f),
                                selectedLabelColor = PhosphorCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Associate EMF Data
                ExposedDropdownMenuBox(
                    expanded = anomalyMenuExpanded,
                    onExpandedChange = { anomalyMenuExpanded = !anomalyMenuExpanded }
                ) {
                    val anomalyDisplay = selectedAnomaly?.let {
                        "${it.severity}: ${"%.1f".format(Locale.US, it.magnitudeUt)} μT (Δ${"%.1f".format(Locale.US, it.deltaUt)} μT)"
                    } ?: "Live EMF Snapshot (${"%.1f".format(Locale.US, currentEmfReading.magnitude)} μT)"
                    OutlinedTextField(
                        value = anomalyDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Associate EMF Data") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = anomalyMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = anomalyMenuExpanded,
                        onDismissRequest = { anomalyMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Live EMF Snapshot (${"%.1f".format(Locale.US, currentEmfReading.magnitude)} μT)") },
                            onClick = {
                                selectedAnomaly = null
                                anomalyMenuExpanded = false
                            }
                        )
                        recentAnomalies.take(10).forEach { a ->
                            DropdownMenuItem(
                                text = {
                                    Text("${a.severity} • ${"%.1f".format(Locale.US, a.magnitudeUt)} μT (Δ${"%.1f".format(Locale.US, a.deltaUt)} μT)")
                                },
                                onClick = {
                                    selectedAnomaly = a
                                    anomalyMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Associate Audio Clip
                ExposedDropdownMenuBox(
                    expanded = clipMenuExpanded,
                    onExpandedChange = { clipMenuExpanded = !clipMenuExpanded }
                ) {
                    val clipDisplay = selectedClip?.fileName ?: "None (Optional Audio Clip)"
                    OutlinedTextField(
                        value = clipDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Associate Audio Clip") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clipMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = clipMenuExpanded,
                        onDismissRequest = { clipMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedClip = null
                                clipMenuExpanded = false
                            }
                        )
                        audioClips.take(12).forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.fileName} (${c.modeLabel})") },
                                onClick = {
                                    selectedClip = c
                                    clipMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (noteText.isNotBlank() || locationLabel.isNotBlank()) {
                            onSaveJournalEntry(
                                locationLabel,
                                noteText.ifBlank { "Location EMF & Audio observation" },
                                selectedTags.joinToString(", "),
                                selectedAnomaly,
                                selectedClip
                            )
                            noteText = ""
                            selectedTags.clear()
                            selectedAnomaly = null
                            selectedClip = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PhosphorCyan,
                        contentColor = VoidBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_journal_entry_button")
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Journal Entry", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Export Journal Bar
        item {
            InstrumentCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "JOURNAL ENTRIES (${journalEntries.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onExportJournalCsv,
                        enabled = journalEntries.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_journal_csv_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = onExportJournalJson,
                        enabled = journalEntries.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_journal_json_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("JSON", style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        onClick = onShareJournal,
                        enabled = journalEntries.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_journal_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (journalEntries.isEmpty()) {
            item {
                InstrumentCard {
                    Text(
                        text = "No field journal entries yet. Add notes above to bind GPS coordinates, live EMF telemetry, and audio captures together.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        } else {
            items(journalEntries, key = { it.id }) { entry ->
                val linkedClip = audioClips.firstOrNull { it.id == entry.associatedClipId }
                JournalEntryCard(
                    entry = entry,
                    linkedClip = linkedClip,
                    onPlayLinkedClip = { linkedClip?.let(onPlayClip) },
                    onDelete = { onDeleteEntry(entry.id) }
                )
            }
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntryEntity,
    linkedClip: AudioClipEntity?,
    onPlayLinkedClip: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFmt = SimpleDateFormat("MMM d • HH:mm:ss", Locale.getDefault())
    InstrumentCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.locationLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = PhosphorCyan
                )
                Text(
                    text = dateFmt.format(Date(entry.timestampMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_item),
                    tint = AnomalyCrimson
                )
            }
        }

        if (entry.latitude != null && entry.longitude != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ElevatedCard)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = GeoportTeal,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${"%.6f".format(Locale.US, entry.latitude)}, ${"%.6f".format(Locale.US, entry.longitude)}" +
                        (entry.gpsAccuracyMeters?.let { " (±${"%.0f".format(Locale.US, it)}m)" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = GeoportTeal
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = entry.noteText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (entry.tags.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tags: ${entry.tags}",
                style = MaterialTheme.typography.labelSmall,
                color = FranksAmber
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "EMF: ${entry.associatedAnomalySummary}",
            style = MaterialTheme.typography.labelSmall,
            color = PhosphorCyan
        )

        if (entry.associatedClipName.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onPlayLinkedClip,
                enabled = linkedClip != null,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (linkedClip != null) Icons.Filled.PlayArrow else Icons.Filled.AudioFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(entry.associatedClipName, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
