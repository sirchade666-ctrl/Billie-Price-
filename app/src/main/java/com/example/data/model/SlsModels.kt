package com.example.data.model

enum class SlsVisualMode(val label: String, val subtitle: String) {
    CLASSIC_GREEN("Classic IR", "Traditional green structured-light stick figure & dot matrix"),
    PHOSPHOR_CYAN("Cyan LIDAR", "High-contrast cyan skeletal wireframe with depth sweep"),
    THERMAL_DEPTH("Thermal", "Amber/crimson figure highlight with low-light grid"),
    RAW_OPTICAL("Raw Optical", "Unfiltered camera feed with clean skeletal overlay")
}

enum class SlsSensitivity(val label: String, val minLikelihood: Float) {
    HIGH("High (30%)", 0.30f),
    STANDARD("Std (55%)", 0.55f),
    STRICT("Strict (75%)", 0.75f)
}

enum class SlsJointType {
    NOSE,
    LEFT_EYE,
    RIGHT_EYE,
    LEFT_EAR,
    RIGHT_EAR,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_PINKY,
    RIGHT_PINKY,
    LEFT_INDEX,
    RIGHT_INDEX,
    LEFT_THUMB,
    RIGHT_THUMB,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE,
    LEFT_HEEL,
    RIGHT_HEEL,
    LEFT_FOOT_INDEX,
    RIGHT_FOOT_INDEX
}

data class SlsJoint(
    val type: SlsJointType,
    val normX: Float,
    val normY: Float,
    val confidence: Float
)

data class SlsMotionCell(
    val normX: Float,
    val normY: Float,
    val intensity: Float
)

data class SlsSkeleton(
    val joints: Map<SlsJointType, SlsJoint> = emptyMap(),
    val avgConfidence: Float = 0f,
    val minX: Float = 0f,
    val minY: Float = 0f,
    val maxX: Float = 0f,
    val maxY: Float = 0f,
    val persistenceAlpha: Float = 1f
) {
    val jointCount: Int get() = joints.size
    val isDetected: Boolean get() = joints.size >= 4
    val isPartialForm: Boolean get() = joints.size in 2..3
    val hasAnyForm: Boolean get() = joints.size >= 2

    fun regionConfidence(types: List<SlsJointType>): Float {
        val matched = types.mapNotNull { joints[it]?.confidence }
        return if (matched.isEmpty()) 0f else matched.average().toFloat()
    }
}

data class SlsFrameTelemetry(
    val skeleton: SlsSkeleton = SlsSkeleton(),
    val motionCells: List<SlsMotionCell> = emptyList(),
    val sourceWidth: Int = 480,
    val sourceHeight: Int = 640,
    val averageLuminance: Float = 0.5f,
    val isLowLight: Boolean = false,
    val fps: Float = 0f,
    val totalSightingsLogged: Int = 0,
    val timestampMs: Long = 0L
)

object SlsSkeletonTopology {
    /**
     * Full ML Kit SLS stick-figure bone pairs including torso, limbs, hands, and feet.
     */
    val boneConnections: List<Pair<SlsJointType, SlsJointType>> = listOf(
        // Shoulders
        SlsJointType.LEFT_SHOULDER to SlsJointType.RIGHT_SHOULDER,
        // Left arm & hand
        SlsJointType.LEFT_SHOULDER to SlsJointType.LEFT_ELBOW,
        SlsJointType.LEFT_ELBOW to SlsJointType.LEFT_WRIST,
        SlsJointType.LEFT_WRIST to SlsJointType.LEFT_INDEX,
        SlsJointType.LEFT_WRIST to SlsJointType.LEFT_PINKY,
        SlsJointType.LEFT_WRIST to SlsJointType.LEFT_THUMB,
        // Right arm & hand
        SlsJointType.RIGHT_SHOULDER to SlsJointType.RIGHT_ELBOW,
        SlsJointType.RIGHT_ELBOW to SlsJointType.RIGHT_WRIST,
        SlsJointType.RIGHT_WRIST to SlsJointType.RIGHT_INDEX,
        SlsJointType.RIGHT_WRIST to SlsJointType.RIGHT_PINKY,
        SlsJointType.RIGHT_WRIST to SlsJointType.RIGHT_THUMB,
        // Torso
        SlsJointType.LEFT_SHOULDER to SlsJointType.LEFT_HIP,
        SlsJointType.RIGHT_SHOULDER to SlsJointType.RIGHT_HIP,
        SlsJointType.LEFT_HIP to SlsJointType.RIGHT_HIP,
        // Left leg & foot
        SlsJointType.LEFT_HIP to SlsJointType.LEFT_KNEE,
        SlsJointType.LEFT_KNEE to SlsJointType.LEFT_ANKLE,
        SlsJointType.LEFT_ANKLE to SlsJointType.LEFT_HEEL,
        SlsJointType.LEFT_ANKLE to SlsJointType.LEFT_FOOT_INDEX,
        SlsJointType.LEFT_HEEL to SlsJointType.LEFT_FOOT_INDEX,
        // Right leg & foot
        SlsJointType.RIGHT_HIP to SlsJointType.RIGHT_KNEE,
        SlsJointType.RIGHT_KNEE to SlsJointType.RIGHT_ANKLE,
        SlsJointType.RIGHT_ANKLE to SlsJointType.RIGHT_HEEL,
        SlsJointType.RIGHT_ANKLE to SlsJointType.RIGHT_FOOT_INDEX,
        SlsJointType.RIGHT_HEEL to SlsJointType.RIGHT_FOOT_INDEX
    )

    val headRegion = listOf(
        SlsJointType.NOSE,
        SlsJointType.LEFT_EYE,
        SlsJointType.RIGHT_EYE,
        SlsJointType.LEFT_EAR,
        SlsJointType.RIGHT_EAR
    )
    val torsoRegion = listOf(
        SlsJointType.LEFT_SHOULDER,
        SlsJointType.RIGHT_SHOULDER,
        SlsJointType.LEFT_HIP,
        SlsJointType.RIGHT_HIP
    )
    val armsRegion = listOf(
        SlsJointType.LEFT_ELBOW,
        SlsJointType.RIGHT_ELBOW,
        SlsJointType.LEFT_WRIST,
        SlsJointType.RIGHT_WRIST,
        SlsJointType.LEFT_INDEX,
        SlsJointType.RIGHT_INDEX
    )
    val legsRegion = listOf(
        SlsJointType.LEFT_KNEE,
        SlsJointType.RIGHT_KNEE,
        SlsJointType.LEFT_ANKLE,
        SlsJointType.RIGHT_ANKLE,
        SlsJointType.LEFT_FOOT_INDEX,
        SlsJointType.RIGHT_FOOT_INDEX
    )
}
