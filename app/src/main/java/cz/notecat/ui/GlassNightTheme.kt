package cz.notecat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class GlassNightColors(
    val backgroundPrimary: Color = Color(0xFF070B12),
    val backgroundSecondary: Color = Color(0xFF101827),
    val glassSurface: Color = Color(0xB21A2332),
    val glassSurfaceElevated: Color = Color(0xCC202B3C),
    val glassBorder: Color = Color(0x663F6EA8),
    val textPrimary: Color = Color(0xFFF5F7FB),
    val textSecondary: Color = Color(0xFFB9C3D4),
    val accentBlue: Color = Color(0xFF78A7E8),
    val accentBlueSoft: Color = Color(0x333D78C8),
    val danger: Color = Color(0xFFFF7C86),
    val processing: Color = Color(0xFFFFC978),
)

@Immutable
data class GlassNightDimensions(
    val screenPadding: Dp = 20.dp,
    val cardRadius: Dp = 24.dp,
    val cardPadding: Dp = 18.dp,
    val cardSpacing: Dp = 14.dp,
    val actionButtonSize: Dp = 72.dp,
    val bottomContentPadding: Dp = 118.dp,
)

@Immutable
data class GlassNightDurations(
    val recordingPulseDuration: Int = 1100,
    val cardAnimationDuration: Int = 220,
    val processingAnimationDuration: Int = 900,
)

object GlassNightTokens {
    val colors = GlassNightColors()
    val dimensions = GlassNightDimensions()
    val durations = GlassNightDurations()
    val typography = Typography(
        headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    )
    val appTitle = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
    val noteText = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
    val noteSecondary = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = colors.textSecondary)
    val actionText = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun GlassNightTheme(content: @Composable () -> Unit) {
    val colors = GlassNightTokens.colors
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = colors.backgroundPrimary,
            surface = colors.glassSurfaceElevated,
            primary = colors.accentBlue,
            onPrimary = colors.backgroundPrimary,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            secondary = colors.accentBlueSoft,
            error = colors.danger,
        ),
        typography = GlassNightTokens.typography,
        content = content,
    )
}

fun Modifier.glassBackground(): Modifier = background(
    Brush.verticalGradient(
        listOf(GlassNightTokens.colors.backgroundPrimary, GlassNightTokens.colors.backgroundSecondary, GlassNightTokens.colors.backgroundPrimary)
    )
)

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    border: BorderStroke = BorderStroke(1.dp, GlassNightTokens.colors.glassBorder),
    contentPadding: PaddingValues = PaddingValues(GlassNightTokens.dimensions.cardPadding),
    content: @Composable (PaddingValues) -> Unit,
) {
    val shape = RoundedCornerShape(GlassNightTokens.dimensions.cardRadius)
    Box(
        modifier
            .clip(shape)
            .background(if (elevated) GlassNightTokens.colors.glassSurfaceElevated else GlassNightTokens.colors.glassSurface)
            .border(border, shape)
    ) { content(contentPadding) }
}
