package cz.notecat.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Vizuální identita NoteCat: klidná, tmavě petrolejová + mátová.
// Žádný vizuální šum, vysoký kontrast, dobře čitelné na slunci.

private val Ink = Color(0xFF16212A)          // hluboká petrolejová čerň
private val InkSoft = Color(0xFF243642)
private val Mint = Color(0xFF2FA98C)         // primární akcent
private val MintDark = Color(0xFF7BD8C2)     // akcent pro tmavý režim
private val Paper = Color(0xFFF7F9F8)        // teplé světlé pozadí
private val PaperCard = Color(0xFFFFFFFF)
private val DarkBg = Color(0xFF101A21)
private val DarkCard = Color(0xFF1A2730)
private val ErrorRed = Color(0xFFBA4A42)
private val ErrorRedDark = Color(0xFFE7897F)

private val LightColors = lightColorScheme(
    primary = Mint,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEE4),
    onPrimaryContainer = Color(0xFF0A3A2E),
    secondary = InkSoft,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE4ECE9),
    onSurfaceVariant = Color(0xFF54646A),
    surfaceContainer = PaperCard,
    surfaceContainerHigh = PaperCard,
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFFB9C6C4),
)

private val DarkColors = darkColorScheme(
    primary = MintDark,
    onPrimary = Color(0xFF07281F),
    primaryContainer = Color(0xFF1D4A3D),
    onPrimaryContainer = Color(0xFFBFEFE0),
    secondary = Color(0xFF9FB4BE),
    onSecondary = Ink,
    background = DarkBg,
    onBackground = Color(0xFFE4ECEA),
    surface = DarkBg,
    onSurface = Color(0xFFE4ECEA),
    surfaceVariant = Color(0xFF243642),
    onSurfaceVariant = Color(0xFF9DB0B5),
    surfaceContainer = DarkCard,
    surfaceContainerHigh = DarkCard,
    error = ErrorRedDark,
    onError = Color(0xFF3B0F0B),
    outline = Color(0xFF44565E),
)

private val NoteCatTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun NoteCatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NoteCatTypography,
        content = content,
    )
}
