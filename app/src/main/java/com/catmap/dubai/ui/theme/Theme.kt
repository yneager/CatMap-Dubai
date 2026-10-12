package com.catmap.dubai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Typography
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.catmap.dubai.data.ThemeMode

// Desert sand, terracotta and Gulf teal.
private val Light = lightColorScheme(
    primary = Color(0xFFA9531A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF361000),
    secondary = Color(0xFF00696B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9CF1F2),
    onSecondaryContainer = Color(0xFF002020),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA3),
    onTertiaryContainer = Color(0xFF261900),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4DED3),
    onSurfaceVariant = Color(0xFF52443C),
    surfaceContainer = Color(0xFFF8EDE6),
    surfaceContainerHigh = Color(0xFFF2E6DF),
    surfaceContainerLow = Color(0xFFFCF2EC),
    outline = Color(0xFF85746B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68D),
    onPrimary = Color(0xFF581E00),
    primaryContainer = Color(0xFF7D2E00),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFF80D4D5),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F50),
    onSecondaryContainer = Color(0xFF9CF1F2),
    tertiary = Color(0xFFF2BF48),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA3),
    background = Color(0xFF1A1410),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF1A1410),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    surfaceContainer = Color(0xFF271F1A),
    surfaceContainerHigh = Color(0xFF322924),
    surfaceContainerLow = Color(0xFF221A15),
    outline = Color(0xFFA08D84),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Generous rounding gives the cards, sheets and buttons a soft, modern look. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        // Body text is mostly what people typed: an English description in the Arabic UI (or the
        // reverse) takes its direction from its own words, so punctuation lands on the right side.
        bodyLarge = bodyLarge.copy(textDirection = TextDirection.Content),
        bodyMedium = bodyMedium.copy(textDirection = TextDirection.Content),
        bodySmall = bodySmall.copy(textDirection = TextDirection.Content),
    )
}

@Composable
fun CatMapTheme(dark: Boolean, largeText: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = if (largeText) Density(density.density, density.fontScale * 1.18f) else density
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** The brand gradient used on the Post button, headers and highlights. */
val BrandGradient = Brush.linearGradient(listOf(Color(0xFFFF7A45), Color(0xFFE5486B), Color(0xFF8E5CF7)))

/** Colours used for status pills; readable in both themes. */
fun statusColor(argb: Long) = Color(argb)
