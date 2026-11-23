package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.EmfReading
import com.example.data.model.OvilusLogEntry
import com.example.data.model.OvilusMode
import com.example.data.model.OvilusState
import com.example.data.model.OvilusVoiceProfile
import com.example.engine.OvilusWordBank
import com.example.ui.components.InstrumentCard
import com.example.ui.components.StatusLed
import com.example.ui.theme.AnomalyCrimson
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.PhosphorCyan
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun OvilusScreen(
    ovilusState: OvilusState,
    emfReading: EmfReading,
    onToggleActive: () -> Unit,
    onManualScan: () -> Unit,
    onModeChange: (OvilusMode) -> Unit,
    onVoiceProfileChange: (OvilusVoiceProfile) -> Unit,
    onSelectDeviceVoice: (String) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onCooldownChange: (Int) -> Unit,
    onAutoLogChange: (Boolean) -> Unit,
    onTestVoice: () -> Unit,
    onSpeakLexiconWord: (Int, String) -> Unit,
    onAskQuestion: (String) -> Unit,
    onReplayEntry: (OvilusLogEntry) -> Unit,
    onPinToJournal: (OvilusLogEntry) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onShareTranscript: () -> Unit,
    onClearLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var questionInput by remember { mutableStateOf("") }
    var lexiconQuery by remember { mutableStateOf("") }
    val lexiconResults = remember(lexiconQuery) {
        OvilusWordBank.searchWords(lexiconQuery, limit = 20)
    }
    val accent = if (ovilusState.isSpeaking) GeoportTeal else PhosphorCyan
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val numFormat = remember { NumberFormat.getIntegerInstance(Locale.US) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Primary Ovilus Output & 80,000-Word Bank Header
        item {
            InstrumentCard(
                borderColor = if (ovilusState.isActive) accent.copy(alpha = 0.7f) else SubtleBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusLed(
                            active = ovilusState.isActive,
                            activeColor = if (ovilusState.isSpeaking) GeoportTeal else PhosphorCyan,
                            pulse = ovilusState.isSpeaking || ovilusState.isActive
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "OVILUS ITC SYNTHESIZER",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${numFormat.format(ovilusState.wordBankCount)}-Word Lexicon • ${if (ovilusState.isTtsReady) "Clear Voice Ready" else "Initializing Voice..."}",
                                style = MaterialTheme.typography.labelSmall,
                                color = GeoportTeal
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElevatedCard)
                            .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (ovilusState.isSpeaking) "SPEAKING" else if (ovilusState.isActive) "LISTENING" else "STANDBY",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ovilusState.isSpeaking) GeoportTeal else if (ovilusState.isActive) PhosphorCyan else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Word Display Chamber
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(VoidBlack)
                        .border(1.5.dp, if (ovilusState.isActive) accent.copy(alpha = 0.55f) else SubtleBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = ovilusState.lastWord.uppercase(Locale.US),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (ovilusState.isActive || ovilusState.lastBankIndex != null) accent else TextMuted,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("ovilus_last_word")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val subInfo = buildString {
                        ovilusState.lastBankIndex?.let {
                            append("Lexicon #${numFormat.format(it + 1)} / ${numFormat.format(ovilusState.wordBankCount)}")
                        } ?: append("Environmental EMF -> Word Matrix")
                        if (ovilusState.lastPhonetic.isNotBlank()) {
                            append(" • [${ovilusState.lastPhonetic}]")
                        }
                    }
                    Text(
                        text = subInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EMF ${"%.1f".format(Locale.US, emfReading.magnitude)} μT",
                            style = MaterialTheme.typography.labelMedium,
                            color = PhosphorCyan
                        )
                        Text(
                            text = "Δ ${"%.1f".format(Locale.US, abs(emfReading.delta))} μT",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (abs(emfReading.delta) >= ovilusState.sensitivityUt) FranksAmber else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onToggleActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ovilusState.isActive) AnomalyCrimson else PhosphorCyan,
                            contentColor = if (ovilusState.isActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ovilus_power_button")
                    ) {
                        Icon(
                            imageVector = if (ovilusState.isActive) Icons.Filled.Stop else Icons.Filled.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (ovilusState.isActive) "Stop Ovilus" else "Start Ovilus",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    FilledTonalButton(
                        onClick = onManualScan,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ovilus_manual_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trigger Word")
                    }
                }
            }
        }

        // Mode & Clear Voice Selector
        item {
            InstrumentCard {
                Text(
                    text = "OVILUS MODE & CLEAR VOICES",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    OvilusMode.entries.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = ovilusState.mode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(idx, OvilusMode.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = accent.copy(alpha = 0.22f),
                                activeContentColor = accent,
                                inactiveContainerColor = ElevatedCard,
                                inactiveContentColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("ovilus_mode_${mode.name.lowercase()}")
                        ) {
                            Text(mode.label, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ovilusState.mode.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice Clarity Profile",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = onTestVoice,
                        modifier = Modifier.testTag("ovilus_test_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Voice")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OvilusVoiceProfile.entries.forEach { profile ->
                        val selected = ovilusState.voiceProfile == profile
                        FilterChip(
                            selected = selected,
                            onClick = { onVoiceProfileChange(profile) },
                            label = { Text(profile.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GeoportTeal.copy(alpha = 0.22f),
                                selectedLabelColor = GeoportTeal
                            ),
                            modifier = Modifier.testTag("voice_profile_${profile.name.lowercase()}")
                        )
                    }
                }

                if (ovilusState.availableVoices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Installed System Voices",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ovilusState.availableVoices.forEach { voice ->
                            val selected = ovilusState.selectedVoiceId == voice.id
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectDeviceVoice(voice.id) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.RecordVoiceOver,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("${voice.displayName} • ${voice.qualityLabel}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PhosphorCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = PhosphorCyan
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Speech Clarity Rate", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("${"%.2f".format(Locale.US, ovilusState.speechRate)}x", style = MaterialTheme.typography.labelLarge, color = PhosphorCyan)
                }
                Slider(
                    value = ovilusState.speechRate,
                    onValueChange = onSpeechRateChange,
                    valueRange = 0.6f..1.3f,
                    colors = SliderDefaults.colors(thumbColor = PhosphorCyan, activeTrackColor = PhosphorCyan)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Voice Pitch", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("${"%.2f".format(Locale.US, ovilusState.pitch)}", style = MaterialTheme.typography.labelLarge, color = GeoportTeal)
                }
                Slider(
                    value = ovilusState.pitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.65f..1.4f,
                    colors = SliderDefaults.colors(thumbColor = GeoportTeal, activeTrackColor = GeoportTeal)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("EMF Flux Trigger Threshold", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("${"%.1f".format(Locale.US, ovilusState.sensitivityUt)} μT", style = MaterialTheme.typography.labelLarge, color = FranksAmber)
                }
                Slider(
                    value = ovilusState.sensitivityUt,
                    onValueChange = onSensitivityChange,
                    valueRange = 1.5f..15.0f,
                    colors = SliderDefaults.colors(thumbColor = FranksAmber, activeTrackColor = FranksAmber)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Response Cooldown", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Text("${ovilusState.cooldownSeconds}s", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                }
                Slider(
                    value = ovilusState.cooldownSeconds.toFloat(),
                    onValueChange = { onCooldownChange(it.toInt()) },
                    valueRange = 2f..15f,
                    colors = SliderDefaults.colors(thumbColor = PhosphorCyan, activeTrackColor = PhosphorCyan)
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Log Words to Active Session",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Saves triggered words with GPS & EMF μT to Journal",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = ovilusState.autoLogToJournal,
                        onCheckedChange = onAutoLogChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = VoidBlack, checkedTrackColor = GeoportTeal),
                        modifier = Modifier.testTag("ovilus_auto_log_switch")
                    )
                }
            }
        }

        // ITC Question Prompt
        item {
            InstrumentCard {
                Text(
                    text = "ASK ITC QUESTION",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        placeholder = { Text("Ask a question to seed EMF scan...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ovilus_question_input")
                    )
                    IconButton(
                        onClick = {
                            if (questionInput.isNotBlank()) {
                                onAskQuestion(questionInput)
                                questionInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ovilus_ask_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Ask Ovilus question",
                            tint = PhosphorCyan
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Is anyone here?", "What is your name?", "Where are you?", "Give us a sign").forEach { prompt ->
                        FilterChip(
                            selected = false,
                            onClick = { onAskQuestion(prompt) },
                            label = { Text(prompt) }
                        )
                    }
                }
            }
        }

        // 80,000-Word Bank Explorer
        item {
            InstrumentCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "80,000-WORD BANK EXPLORER",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Tap word to hear",
                        style = MaterialTheme.typography.labelSmall,
                        color = GeoportTeal
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lexiconQuery,
                    onValueChange = { lexiconQuery = it },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    },
                    placeholder = { Text("Search 80K lexicon (e.g., basement, cold, shadow)...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ovilus_lexicon_search_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lexiconResults.forEach { (idx, word) ->
                        FilterChip(
                            selected = ovilusState.lastBankIndex == idx,
                            onClick = { onSpeakLexiconWord(idx, word) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = { Text("$word (#${numFormat.format(idx + 1)})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PhosphorCyan.copy(alpha = 0.22f),
                                selectedLabelColor = PhosphorCyan
                            )
                        )
                    }
                }
            }
        }

        // Word Bank Log Header & Transcript Exports
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OVILUS WORD TRANSCRIPT (${ovilusState.logEntries.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    if (ovilusState.logEntries.isNotEmpty()) {
                        TextButton(onClick = onClearLog) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AnomalyCrimson
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", color = AnomalyCrimson)
                        }
                    }
                }
                if (ovilusState.logEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onExportCsv,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ovilus_export_csv_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CSV", style = MaterialTheme.typography.labelMedium)
                        }
                        FilledTonalButton(
                            onClick = onExportJson,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ovilus_export_json_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("JSON", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = onShareTranscript,
                            colors = ButtonDefaults.buttonColors(containerColor = GeoportTeal, contentColor = VoidBlack),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ovilus_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (ovilusState.logEntries.isEmpty()) {
            item {
                InstrumentCard {
                    Text(
                        text = "No words triggered yet. Start Ovilus to map live EMF fluctuations across the 80,000-word bank, or tap Trigger Word.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        } else {
            items(ovilusState.logEntries, key = { it.id }) { entry ->
                InstrumentCard(
                    borderColor = if (entry.isQuestion) FranksAmber.copy(alpha = 0.45f) else SubtleBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (entry.isQuestion) "INVESTIGATOR PROMPT" else "${timeFormat.format(Date(entry.timestampMs))} • ${"%.1f".format(Locale.US, entry.emfMagnitudeUt)} μT (Δ${"%.1f".format(Locale.US, entry.emfDeltaUt)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.isQuestion) FranksAmber else TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (entry.isQuestion) "\"${entry.text}\"" else entry.text.uppercase(Locale.US),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (entry.isQuestion) MaterialTheme.colorScheme.onSurface else PhosphorCyan,
                                fontWeight = FontWeight.Bold
                            )
                            if (!entry.isQuestion && entry.bankIndex != null) {
                                Text(
                                    text = "Bank #${numFormat.format(entry.bankIndex + 1)} • [${entry.phoneticCue}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        if (!entry.isQuestion) {
                            Row {
                                IconButton(
                                    onClick = { onReplayEntry(entry) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Speak word again",
                                        tint = GeoportTeal
                                    )
                                }
                                IconButton(
                                    onClick = { onPinToJournal(entry) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.BookmarkAdd,
                                        contentDescription = "Save Ovilus word to journal",
                                        tint = FranksAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
