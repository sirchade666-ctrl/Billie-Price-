package com.example.engine

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.data.model.SlsFrameTelemetry
import com.example.data.model.SlsJoint
import com.example.data.model.SlsJointType
import com.example.data.model.SlsMotionCell
import com.example.data.model.SlsSensitivity
import com.example.data.model.SlsSkeleton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Real-time CameraX ImageAnalysis.Analyzer combining on-device ML Kit Pose Detection
 * (full upper/lower body, hands, and feet landmarks with EMA anti-jitter smoothing)
 * and a Y-plane structured-light motion/luminance grid for SLS Camera Mode.
 */
class SlsPoseAnalyzer(
    private val isFrontCamera: () -> Boolean,
    private val sensitivityProvider: () -> SlsSensitivity,
    private val smoothingEnabledProvider: () -> Boolean = { true },
    private val onTelemetryUpdate: (SlsFrameTelemetry) -> Unit
) : ImageAnalysis.Analyzer {

    private val poseDetector: PoseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    private var prevGrid: FloatArray? = null
    private var lastDetectedSkeleton: SlsSkeleton? = null
    private var lastDetectedTimestampMs = 0L
    private var frameCounter = 0
    private var lastFpsTimestampMs = 0L
    private var measuredFps = 0f

    @Volatile
    private var isClosed = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (isClosed) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val uprightWidth = if (isRotated) imageProxy.height else imageProxy.width
        val uprightHeight = if (isRotated) imageProxy.width else imageProxy.height
        val mirrorX = isFrontCamera()
        val minConfidence = sensitivityProvider().minLikelihood
        val applySmoothing = smoothingEnabledProvider()

        // Compute real Y-plane ambient luminance & structured-light motion grid
        val (avgLuma, motionCells) = computeLumaAndMotionGrid(
            imageProxy = imageProxy,
            rotationDegrees = rotationDegrees,
            mirrorX = mirrorX
        )

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        poseDetector.process(inputImage)
            .addOnSuccessListener { pose ->
                val now = System.currentTimeMillis()
                frameCounter++
                if (lastFpsTimestampMs == 0L) {
                    lastFpsTimestampMs = now
                } else if (now - lastFpsTimestampMs >= 1000L) {
                    measuredFps = frameCounter * 1000f / (now - lastFpsTimestampMs).coerceAtLeast(1L)
                    frameCounter = 0
                    lastFpsTimestampMs = now
                }

                val rawSkeleton = buildSkeletonFromPose(
                    pose = pose,
                    uprightWidth = uprightWidth,
                    uprightHeight = uprightHeight,
                    mirrorX = mirrorX,
                    minConfidence = minConfidence
                )

                val displaySkeleton = if (rawSkeleton.hasAnyForm) {
                    val smoothed = if (applySmoothing) {
                        smoothSkeleton(lastDetectedSkeleton, rawSkeleton, alpha = 0.48f)
                    } else {
                        rawSkeleton
                    }
                    lastDetectedSkeleton = smoothed
                    lastDetectedTimestampMs = now
                    smoothed
                } else {
                    // Brief persistence decay (380ms) prevents harsh flicker in dim lighting
                    val elapsedMs = now - lastDetectedTimestampMs
                    val prev = lastDetectedSkeleton
                    if (applySmoothing && prev != null && elapsedMs in 1L..380L) {
                        val fade = (1f - elapsedMs / 380f).coerceIn(0.15f, 1f)
                        prev.copy(persistenceAlpha = fade)
                    } else {
                        lastDetectedSkeleton = null
                        SlsSkeleton()
                    }
                }

                onTelemetryUpdate(
                    SlsFrameTelemetry(
                        skeleton = displaySkeleton,
                        motionCells = motionCells,
                        sourceWidth = uprightWidth.coerceAtLeast(1),
                        sourceHeight = uprightHeight.coerceAtLeast(1),
                        averageLuminance = avgLuma,
                        isLowLight = avgLuma < 0.22f,
                        fps = measuredFps,
                        timestampMs = now
                    )
                )
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    fun close() {
        isClosed = true
        runCatching { poseDetector.close() }
    }

    private fun computeLumaAndMotionGrid(
        imageProxy: ImageProxy,
        rotationDegrees: Int,
        mirrorX: Boolean
    ): Pair<Float, List<SlsMotionCell>> {
        val yPlane = imageProxy.planes.firstOrNull() ?: return 0.5f to emptyList()
        val buffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val width = imageProxy.width
        val height = imageProxy.height
        if (width <= 0 || height <= 0) return 0.5f to emptyList()

        val cols = 12
        val rows = 16
        val currentGrid = FloatArray(cols * rows)
        var lumaSum = 0f
        val capacity = buffer.capacity()

        for (r in 0 until rows) {
            val srcY = ((r + 0.5f) / rows * height).toInt().coerceIn(0, height - 1)
            for (c in 0 until cols) {
                val srcX = ((c + 0.5f) / cols * width).toInt().coerceIn(0, width - 1)
                val idx = srcY * rowStride + srcX * pixelStride
                val luma = if (idx in 0 until capacity) {
                    (buffer.get(idx).toInt() and 0xFF) / 255f
                } else {
                    0f
                }
                currentGrid[r * cols + c] = luma
                lumaSum += luma
            }
        }

        val avgLuma = lumaSum / (cols * rows)
        val previous = prevGrid
        prevGrid = currentGrid

        if (previous == null || previous.size != currentGrid.size) {
            return avgLuma to emptyList()
        }

        val hotspots = mutableListOf<SlsMotionCell>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val diff = abs(currentGrid[r * cols + c] - previous[r * cols + c])
                if (diff >= 0.085f) {
                    val rawNormX = (c + 0.5f) / cols
                    val rawNormY = (r + 0.5f) / rows
                    val (rotX, rotY) = rotateNormalizedPoint(rawNormX, rawNormY, rotationDegrees)
                    val finalX = if (mirrorX) 1f - rotX else rotX
                    hotspots.add(
                        SlsMotionCell(
                            normX = finalX.coerceIn(0f, 1f),
                            normY = rotY.coerceIn(0f, 1f),
                            intensity = ((diff - 0.085f) / 0.25f).coerceIn(0.15f, 1f)
                        )
                    )
                }
            }
        }
        return avgLuma to hotspots
    }

    companion object {
        private val landmarkMapping: List<Pair<Int, SlsJointType>> = listOf(
            PoseLandmark.NOSE to SlsJointType.NOSE,
            PoseLandmark.LEFT_EYE to SlsJointType.LEFT_EYE,
            PoseLandmark.RIGHT_EYE to SlsJointType.RIGHT_EYE,
            PoseLandmark.LEFT_EAR to SlsJointType.LEFT_EAR,
            PoseLandmark.RIGHT_EAR to SlsJointType.RIGHT_EAR,
            PoseLandmark.LEFT_SHOULDER to SlsJointType.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER to SlsJointType.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW to SlsJointType.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to SlsJointType.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST to SlsJointType.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST to SlsJointType.RIGHT_WRIST,
            PoseLandmark.LEFT_PINKY to SlsJointType.LEFT_PINKY,
            PoseLandmark.RIGHT_PINKY to SlsJointType.RIGHT_PINKY,
            PoseLandmark.LEFT_INDEX to SlsJointType.LEFT_INDEX,
            PoseLandmark.RIGHT_INDEX to SlsJointType.RIGHT_INDEX,
            PoseLandmark.LEFT_THUMB to SlsJointType.LEFT_THUMB,
            PoseLandmark.RIGHT_THUMB to SlsJointType.RIGHT_THUMB,
            PoseLandmark.LEFT_HIP to SlsJointType.LEFT_HIP,
            PoseLandmark.RIGHT_HIP to SlsJointType.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE to SlsJointType.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE to SlsJointType.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE to SlsJointType.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE to SlsJointType.RIGHT_ANKLE,
            PoseLandmark.LEFT_HEEL to SlsJointType.LEFT_HEEL,
            PoseLandmark.RIGHT_HEEL to SlsJointType.RIGHT_HEEL,
            PoseLandmark.LEFT_FOOT_INDEX to SlsJointType.LEFT_FOOT_INDEX,
            PoseLandmark.RIGHT_FOOT_INDEX to SlsJointType.RIGHT_FOOT_INDEX
        )

        fun normalizeLandmarkCoordinate(
            px: Float,
            py: Float,
            imageWidth: Int,
            imageHeight: Int,
            mirrorX: Boolean
        ): Pair<Float, Float> {
            if (imageWidth <= 0 || imageHeight <= 0) return 0f to 0f
            val nx = (px / imageWidth.toFloat()).coerceIn(0f, 1f)
            val ny = (py / imageHeight.toFloat()).coerceIn(0f, 1f)
            return (if (mirrorX) 1f - nx else nx) to ny
        }

        fun rotateNormalizedPoint(nx: Float, ny: Float, rotationDegrees: Int): Pair<Float, Float> {
            return when ((rotationDegrees % 360 + 360) % 360) {
                90 -> (1f - ny) to nx
                180 -> (1f - nx) to (1f - ny)
                270 -> ny to (1f - nx)
                else -> nx to ny
            }
        }

        /**
         * Projects a normalized [0..1] camera frame coordinate onto a PreviewView viewport
         * configured with ScaleType.FILL_CENTER so the skeletal overlay stays pixel-aligned
         * regardless of aspect ratio mismatch between the camera buffer and the screen box.
         */
        fun mapNormalizedToFillCenterViewport(
            normX: Float,
            normY: Float,
            sourceWidth: Int,
            sourceHeight: Int,
            viewWidth: Float,
            viewHeight: Float
        ): Pair<Float, Float> {
            if (sourceWidth <= 0 || sourceHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) {
                return (normX * viewWidth) to (normY * viewHeight)
            }
            val srcW = sourceWidth.toFloat()
            val srcH = sourceHeight.toFloat()
            val scale = max(viewWidth / srcW, viewHeight / srcH)
            val scaledW = srcW * scale
            val scaledH = srcH * scale
            val offsetX = (viewWidth - scaledW) * 0.5f
            val offsetY = (viewHeight - scaledH) * 0.5f
            return (offsetX + normX * scaledW) to (offsetY + normY * scaledH)
        }

        /**
         * Applies exponential moving average (EMA) smoothing to skeletal joint positions
         * so the live SLS stick-figure tracks smoothly without high-frequency ML jitter.
         */
        fun smoothSkeleton(
            previous: SlsSkeleton?,
            current: SlsSkeleton,
            alpha: Float = 0.48f
        ): SlsSkeleton {
            if (previous == null || previous.joints.isEmpty() || current.joints.isEmpty()) {
                return current.copy(persistenceAlpha = 1f)
            }
            val clampedAlpha = alpha.coerceIn(0.15f, 1f)
            val smoothedJoints = mutableMapOf<SlsJointType, SlsJoint>()
            var minX = 1f
            var minY = 1f
            var maxX = 0f
            var maxY = 0f
            var confSum = 0f

            for ((type, cur) in current.joints) {
                val prev = previous.joints[type]
                val blended = if (prev != null) {
                    SlsJoint(
                        type = type,
                        normX = prev.normX + (cur.normX - prev.normX) * clampedAlpha,
                        normY = prev.normY + (cur.normY - prev.normY) * clampedAlpha,
                        confidence = prev.confidence + (cur.confidence - prev.confidence) * clampedAlpha
                    )
                } else {
                    cur
                }
                smoothedJoints[type] = blended
                minX = min(minX, blended.normX)
                minY = min(minY, blended.normY)
                maxX = max(maxX, blended.normX)
                maxY = max(maxY, blended.normY)
                confSum += blended.confidence
            }

            return SlsSkeleton(
                joints = smoothedJoints,
                avgConfidence = confSum / smoothedJoints.size,
                minX = minX,
                minY = minY,
                maxX = maxX,
                maxY = maxY,
                persistenceAlpha = 1f
            )
        }

        fun buildSkeletonFromPose(
            pose: Pose,
            uprightWidth: Int,
            uprightHeight: Int,
            mirrorX: Boolean,
            minConfidence: Float
        ): SlsSkeleton {
            val joints = mutableMapOf<SlsJointType, SlsJoint>()
            var confSum = 0f
            var minX = 1f
            var minY = 1f
            var maxX = 0f
            var maxY = 0f

            for ((mlType, jointType) in landmarkMapping) {
                val lm = pose.getPoseLandmark(mlType) ?: continue
                if (lm.inFrameLikelihood < minConfidence) continue
                val (nx, ny) = normalizeLandmarkCoordinate(
                    px = lm.position.x,
                    py = lm.position.y,
                    imageWidth = uprightWidth,
                    imageHeight = uprightHeight,
                    mirrorX = mirrorX
                )
                joints[jointType] = SlsJoint(
                    type = jointType,
                    normX = nx,
                    normY = ny,
                    confidence = lm.inFrameLikelihood
                )
                confSum += lm.inFrameLikelihood
                minX = min(minX, nx)
                minY = min(minY, ny)
                maxX = max(maxX, nx)
                maxY = max(maxY, ny)
            }

            if (joints.isEmpty()) return SlsSkeleton()
            return SlsSkeleton(
                joints = joints,
                avgConfidence = confSum / joints.size,
                minX = minX,
                minY = minY,
                maxX = maxX,
                maxY = maxY,
                persistenceAlpha = 1f
            )
        }
    }
}
