package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.EmfReading
import com.example.data.model.RecorderState
import com.example.data.model.SpiritBoxMode
import com.example.data.model.SpiritBoxState
import com.example.data.model.SweepDirection
import com.example.ui.components.InstrumentCard
import com.example.ui.components.StatusLed
import com.example.ui.components.formatDurationMs
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
import java.util.Locale

@Composable
fun SpiritBoxScreen(
    spiritState: SpiritBoxState,
    spectrumBars: FloatArray,
    emfReading: EmfReading,
    recorderState: RecorderState,
    onToggleSpiritBox: () -> Unit,
    onModeChange: (SpiritBoxMode) -> Unit,
    onSweepRateChange: (Int) -> Unit,
    onDirectionChange: (SweepDirection) -> Unit,
    onEchoChange: (Float) -> Unit,
    onSquelchChange: (Float) -> Unit,
    onGainChange: (Float) -> Unit,
    onEmfCoupledChange: (Boolean) -> Unit,
    onRecordOrStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (spiritState.mode == SpiritBoxMode.FRANKS_BOX) FranksAmber else PhosphorCyan

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Engine Mode Selector (Frank's Box vs Geoport ITC)
        item {
            InstrumentCard {
                Text(
                    text = "ITC SYNTHESIS ENGINE",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SpiritBoxMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = spiritState.mode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, SpiritBoxMode.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = accent.copy(alpha = 0.22f),
                                activeContentColor = accent,
                                inactiveContainerColor = ElevatedCard,
                                inactiveContentColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("mode_${mode.name.lowercase()}")
                        ) {
                            Text(
                                text = mode.label,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = spiritState.mode.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Frequency Dial & Spectrum Visualizer
        item {
            val cdAudioWave = stringResource(R.string.cd_audio_waveform)
            InstrumentCard(
                borderColor = if (spiritState.isRunning) accent.copy(alpha = 0.7f) else SubtleBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusLed(
                            active = spiritState.isRunning,
                            activeColor = accent,
                            pulse = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (spiritState.isRunning) "SWEEPING CARRIER" else "UNIT STANDBY",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (spiritState.isRunning) accent else TextMuted
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElevatedCard)
                            .border(1.dp, SubtleBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusLed(
                            active = spiritState.isRunning && spiritState.currentGateOpen,
                            activeColor = GeoportTeal
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (spiritState.currentGateOpen) "GATE OPEN" else "SQUELCHED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (spiritState.currentGateOpen) GeoportTeal else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "${"%.1f".format(Locale.US, spiritState.currentFrequencyMhz)} MHz",
                            style = MaterialTheme.typography.displaySmall,
                            color = accent
                        )
                        Text(
                            text = "${spiritState.direction.label} • ${spiritState.sweepRateMs} ms/step",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }

                    if (spiritState.emfCoupled) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "EMF MOD",
                                style = MaterialTheme.typography.labelSmall,
                                color = GeoportTeal
                            )
                            Text(
                                text = "Δ${"%.1f".format(Locale.US, emfReading.delta)} μT",
                                style = MaterialTheme.typography.titleMedium,
                                color = GeoportTeal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoidBlack)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(10.dp))
                        .semantics { contentDescription = cdAudioWave }
                ) {
                    SpectrumVisualizerCanvas(
                        bars = spectrumBars,
                        barColor = accent,
                        isActive = spiritState.isRunning,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onToggleSpiritBox,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (spiritState.isRunning) AnomalyCrimson else accent,
                            contentColor = if (spiritState.isRunning) Color.White else VoidBlack
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("toggle_spirit_box_button")
                    ) {
                        Icon(
                            imageVector = if (spiritState.isRunning) Icons.Filled.PowerSettingsNew else Icons.Filled.Radio,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (spiritState.isRunning) "Stop Sweep" else "Start Sweep",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onRecordOrStopClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (recorderState.isRecording) AnomalyCrimson else ElevatedCard,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .border(
                                1.dp,
                                if (recorderState.isRecording) AnomalyCrimson else SubtleBorder,
                                MaterialTheme.shapes.extraLarge
                            )
                            .testTag("spirit_box_record_button")
                    ) {
                        Icon(
                            imageVector = if (recorderState.isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                            contentDescription = stringResource(R.string.cd_record_toggle),
                            tint = if (recorderState.isRecording) Color.White else AnomalyCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (recorderState.isRecording) {
                                formatDurationMs(recorderState.elapsedMs)
                            } else {
                                "Record WAV"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Sweep & Geoport Chamber Tuning
        item {
            InstrumentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SWEEP & ACOUSTIC CONTROLS",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sweep Direction",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SweepDirection.entries.forEach { dir ->
                        val selected = spiritState.direction == dir
                        FilterChip(
                            selected = selected,
                            onClick = { onDirectionChange(dir) },
                            label = { Text(dir.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                selectedLabelColor = accent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sweep_dir_${dir.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ControlSlider(
                    label = "Sweep Rate",
                    valueText = "${spiritState.sweepRateMs} ms",
                    value = spiritState.sweepRateMs.toFloat(),
                    range = 50f..400f,
                    accent = accent,
                    onValueChange = { onSweepRateChange(it.toInt()) },
                    tag = "sweep_rate_slider"
                )

                ControlSlider(
                    label = "Geoport Echo / Reverb",
                    valueText = "${(spiritState.echoDepth * 100).toInt()}%",
                    value = spiritState.echoDepth,
                    range = 0f..0.85f,
                    accent = accent,
                    onValueChange = onEchoChange,
                    tag = "echo_depth_slider"
                )

                ControlSlider(
                    label = "Noise Gate / Squelch",
                    valueText = "${(spiritState.squelchLevel * 100).toInt()}%",
                    value = spiritState.squelchLevel,
                    range = 0f..0.9f,
                    accent = accent,
                    onValueChange = onSquelchChange,
                    tag = "squelch_slider"
                )

                ControlSlider(
                    label = "Master Output Gain",
                    valueText = "${(spiritState.gain * 100).toInt()}%",
                    value = spiritState.gain,
                    range = 0.1f..1f,
                    accent = accent,
                    onValueChange = onGainChange,
                    tag = "gain_slider"
                )

                Spacer(modifier = Modifier.height(4.dp))

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
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = GeoportTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "EMF-Coupled Gate & Pitch",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Magnetometer spikes open squelch & shift carrier",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                    Switch(
                        checked = spiritState.emfCoupled,
                        onCheckedChange = onEmfCoupledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = VoidBlack,
                            checkedTrackColor = GeoportTeal
                        ),
                        modifier = Modifier.testTag("emf_coupled_switch")
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onValueChange: (Float) -> Unit,
    tag: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun SpectrumVisualizerCanvas(
    bars: FloatArray,
    barColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val count = bars.size.coerceAtLeast(1)
        val totalSpacing = size.width * 0.25f
        val barWidth = (size.width - totalSpacing) / count
        val gap = totalSpacing / (count - 1).coerceAtLeast(1)

        bars.forEachIndexed { idx, amp ->
            val clamped = if (isActive) amp.coerceIn(0.06f, 1f) else 0.04f
            val barH = size.height * clamped
            val x = idx * (barWidth + gap)
            val y = (size.height - barH) / 2f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(barColor, barColor.copy(alpha = 0.4f))
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}
