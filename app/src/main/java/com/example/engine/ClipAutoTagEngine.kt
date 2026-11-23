package com.example.engine

import com.example.data.local.FlaggedAudioSegmentEntity
import com.example.data.model.AudioMetadataTag
import kotlin.math.abs

data class AutoTagEvaluation(
    val tags: List<String>,
    val serializedTags: String,
    val speechProbabilityPct: Int
)

/**
 * Automatically evaluates sensor triggers and acoustic/phonetic analysis outputs
 * to tag audio clips with structured investigation metadata (e.g. 'High EMF',
 * 'Sharp Temperature Drop', 'High Speech Probability').
 */
object ClipAutoTagEngine {

    const val DEFAULT_SHARP_TEMP_DROP_C = -1.5f
    const val DEFAULT_HIGH_SPEECH_PROB_PCT = 70

    fun evaluateTags(
        peakEmfUt: Float,
        peakEmfDeltaUt: Float,
        baselineEmfUt: Float,
        emfThresholdUt: Float,
        tempDropC: Float,
        modeLabel: String,
        flaggedSegments: List<FlaggedAudioSegmentEntity> = emptyList(),
        phoneticSegments: List<PhoneticSpeechSegment> = emptyList(),
        sharpTempDropThresholdC: Float = DEFAULT_SHARP_TEMP_DROP_C,
        highSpeechProbThresholdPct: Int = DEFAULT_HIGH_SPEECH_PROB_PCT
    ): AutoTagEvaluation {
        val tags = LinkedHashSet<String>()

        // 1. High EMF trigger: peak delta >= threshold, magnitude above baseline+threshold, or flagged segment exceeded threshold
        val effectiveThreshold = emfThresholdUt.coerceAtLeast(1.0f)
        val segmentTriggeredEmf = flaggedSegments.any { abs(it.emfDeltaUt) >= effectiveThreshold }
        val highEmf = abs(peakEmfDeltaUt) >= effectiveThreshold ||
            (baselineEmfUt > 0f && (peakEmfUt - baselineEmfUt) >= effectiveThreshold) ||
            peakEmfUt >= (baselineEmfUt.coerceAtLeast(45f) + effectiveThreshold * 1.4f) ||
            segmentTriggeredEmf

        if (highEmf) {
            tags.add(AudioMetadataTag.HIGH_EMF.label)
        }

        // 2. Sharp Temperature Drop trigger
        if (tempDropC <= sharpTempDropThresholdC) {
            tags.add(AudioMetadataTag.SHARP_TEMP_DROP.label)
        }

        // 3. Speech Probability & Phonetic Pattern triggers
        val maxPhoneticConfidence = phoneticSegments.maxOfOrNull { seg ->
            val bonus = if (seg.isRepeatedPattern) 6 else 0
            (seg.confidenceScore + bonus).coerceAtMost(99)
        } ?: 0

        val maxVocalFlagConfidence = flaggedSegments
            .filter {
                it.acousticClass.contains("Vocal", ignoreCase = true) ||
                    it.acousticClass.contains("Whisper", ignoreCase = true) ||
                    it.acousticClass.contains("Formant", ignoreCase = true) ||
                    it.acousticClass.contains("Speech", ignoreCase = true)
            }
            .maxOfOrNull { it.confidenceScore } ?: 0

        val speechProbabilityPct = maxOf(maxPhoneticConfidence, maxVocalFlagConfidence).coerceIn(0, 100)
        val hasRepeatedPattern = phoneticSegments.any { it.isRepeatedPattern }

        if (speechProbabilityPct >= highSpeechProbThresholdPct) {
            tags.add(AudioMetadataTag.HIGH_SPEECH_PROB.label)
        }
        if (hasRepeatedPattern) {
            tags.add(AudioMetadataTag.REPEATED_PATTERN.label)
        }

        // 4. Concurrent EVP+EMF Co-Incidence trigger
        if (flaggedSegments.isNotEmpty()) {
            tags.add(AudioMetadataTag.EVP_EMF_COINCIDENCE.label)
        }

        // 5. Spirit Box Mix metadata
        if (modeLabel.contains("Frank", ignoreCase = true) ||
            modeLabel.contains("Geoport", ignoreCase = true) ||
            modeLabel.contains("+ Mic", ignoreCase = true)
        ) {
            tags.add(AudioMetadataTag.SPIRIT_BOX_MIX.label)
        }

        val tagList = tags.toList()
        return AutoTagEvaluation(
            tags = tagList,
            serializedTags = formatTags(tagList),
            speechProbabilityPct = speechProbabilityPct
        )
    }

    fun formatTags(tags: List<String>): String =
        tags.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

    fun parseTags(raw: String): List<String> =
        if (raw.isBlank()) {
            emptyList()
        } else {
            raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }
}
