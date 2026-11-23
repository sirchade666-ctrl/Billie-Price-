package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val LocalNightVisionEnabled = compositionLocalOf { false }
val LocalNightVisionBrightness = compositionLocalOf { 0.90f }

private fun buildNightVisionMatrix(brightness: Float): ColorMatrix {
    val b = brightness.coerceIn(0.40f, 1.15f)
    return ColorMatrix(
        floatArrayOf(
            0.92f * b, 0.62f * b, 0.28f * b, 0f, 10f * b,
            0.06f * b, 0.14f * b, 0.04f * b, 0f, 0f,
            0.02f * b, 0.02f * b, 0.04f * b, 0f, 0f,
            0.00f, 0.00f, 0.00f, 1f, 0f
        )
    )
}

@Composable
fun Modifier.nightVisionOverlay(
    enabled: Boolean,
    brightness: Float = LocalNightVisionBrightness.current
): Modifier {
    if (!enabled) return this
    val clamped = brightness.coerceIn(0.40f, 1.15f)
    val layerPaint = remember(clamped) {
        Paint().apply {
            colorFilter = ColorFilter.colorMatrix(buildNightVisionMatrix(clamped))
        }
    }
    return this.drawWithContent {
        val bounds = Rect(0f, 0f, size.width, size.height)
        drawContext.canvas.saveLayer(bounds, layerPaint)
        drawContent()
        drawContext.canvas.restore()
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x0A800000),
                    Color(0x1E400000)
                ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height).coerceAtLeast(1f) * 0.85f
            )
        )
    }
}

private val GeoportDarkColorScheme = darkColorScheme(
    primary = PhosphorCyan,
    onPrimary = VoidBlack,
    primaryContainer = PhosphorCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = FranksAmber,
    onSecondary = VoidBlack,
    secondaryContainer = FranksAmberDim,
    onSecondaryContainer = TextPrimary,
    tertiary = GeoportTeal,
    onTertiary = VoidBlack,
    error = AnomalyCrimson,
    onError = TextPrimary,
    errorContainer = AnomalyCrimsonDim,
    onErrorContainer = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = PanelSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    outline = SubtleBorder,
    outlineVariant = SubtleBorder
)

private val GeoportNightVisionColorScheme = darkColorScheme(
    primary = NightRedPrimary,
    onPrimary = NightVoidBlack,
    primaryContainer = NightRedPrimaryDim,
    onPrimaryContainer = NightTextPrimary,
    secondary = NightRedSecondary,
    onSecondary = NightVoidBlack,
    secondaryContainer = NightRedSecondaryDim,
    onSecondaryContainer = NightTextPrimary,
    tertiary = NightRedTertiary,
    onTertiary = NightVoidBlack,
    error = NightRedPrimary,
    onError = NightVoidBlack,
    errorContainer = NightRedPrimaryDim,
    onErrorContainer = NightTextPrimary,
    background = NightObsidianBg,
    onBackground = NightTextPrimary,
    surface = NightPanelSurface,
    onSurface = NightTextPrimary,
    surfaceVariant = NightElevatedCard,
    onSurfaceVariant = NightTextSecondary,
    outline = NightSubtleBorder,
    outlineVariant = NightSubtleBorder
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun GeoportEmfTheme(
    nightVision: Boolean = false,
    nightVisionBrightness: Float = 0.90f,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalNightVisionEnabled provides nightVision,
        LocalNightVisionBrightness provides nightVisionBrightness.coerceIn(0.40f, 1.15f)
    ) {
        MaterialTheme(
            colorScheme = if (nightVision) GeoportNightVisionColorScheme else GeoportDarkColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
