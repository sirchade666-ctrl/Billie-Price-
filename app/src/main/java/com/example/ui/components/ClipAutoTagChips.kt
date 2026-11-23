package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AudioMetadataTag
import com.example.engine.ClipAutoTagEngine
import com.example.ui.theme.AnomalyCrimson
import com.example.ui.theme.FranksAmber
import com.example.ui.theme.GeoportTeal
import com.example.ui.theme.PhosphorCyan

val SpeechViolet = Color(0xFFC084FC)
val TempIceBlue = Color(0xFF38BDF8)

fun clipTagColor(tagLabel: String): Color {
    return when (AudioMetadataTag.fromLabel(tagLabel)) {
        AudioMetadataTag.HIGH_EMF -> AnomalyCrimson
        AudioMetadataTag.SHARP_TEMP_DROP -> TempIceBlue
        AudioMetadataTag.HIGH_SPEECH_PROB -> SpeechViolet
        AudioMetadataTag.REPEATED_PATTERN -> Color(0xFFE879F9)
        AudioMetadataTag.EVP_EMF_COINCIDENCE -> FranksAmber
        AudioMetadataTag.SPIRIT_BOX_MIX -> GeoportTeal
        null -> PhosphorCyan
    }
}

fun clipTagIcon(tagLabel: String): ImageVector {
    return when (AudioMetadataTag.fromLabel(tagLabel)) {
        AudioMetadataTag.HIGH_EMF -> Icons.Filled.Bolt
        AudioMetadataTag.SHARP_TEMP_DROP -> Icons.Filled.AcUnit
        AudioMetadataTag.HIGH_SPEECH_PROB -> Icons.Filled.RecordVoiceOver
        AudioMetadataTag.REPEATED_PATTERN -> Icons.Filled.Repeat
        AudioMetadataTag.EVP_EMF_COINCIDENCE -> Icons.Filled.AutoAwesome
        AudioMetadataTag.SPIRIT_BOX_MIX -> Icons.Filled.GraphicEq
        null -> Icons.Filled.AutoAwesome
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipAutoTagFlow(
    rawTags: String,
    modifier: Modifier = Modifier
) {
    val tags = ClipAutoTagEngine.parseTags(rawTags)
    if (tags.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEach { tag ->
            ClipAutoTagPill(tag = tag)
        }
    }
}

@Composable
fun ClipAutoTagPill(
    tag: String,
    modifier: Modifier = Modifier
) {
    val accent = clipTagColor(tag)
    val icon = clipTagIcon(tag)
    val safeTagId = tag.lowercase().replace(Regex("[^a-z0-9]+"), "_")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .testTag("clip_tag_pill_$safeTagId"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}
