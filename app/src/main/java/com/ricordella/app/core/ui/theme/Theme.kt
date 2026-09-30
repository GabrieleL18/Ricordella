/* Hallmark · genre: playful · theme: Hum (adattato a Compose) · anchor: giallo-saetta + azzurro vetro
 * paper: crema oklch(97% 0.012 95) · notte: oklch(17% 0.02 265) · display: Plus Jakarta Sans 700/800
 * motion: push-button · spunta con esplosione di stelle · mascotte palla di vetro
 */
package com.ricordella.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.ricordella.app.R
import com.ricordella.app.domain.model.ThemeMode

/** Coppia di colori per una superficie colorata: sfondo tenue + contenuto leggibile + tinta piena. */
@Immutable
data class Tone(val container: Color, val content: Color, val solid: Color)

/**
 * Colori semantici oltre a Material: la saetta (azione principale), i cinque toni
 * di accento (ognuno con il suo significato) e i colori della mascotte.
 */
@Immutable
data class RicordellaColors(
    val bolt: Color,
    val onBolt: Color,
    val boltEdge: Color,
    val boltCast: Color,
    val cyan: Tone,
    val pear: Tone,
    val coral: Tone,
    val mint: Tone,
    val lavender: Tone,
    val glassLight: Color,
    val glass: Color,
    val glassDeep: Color,
    val glassRim: Color,
    val glassHighlight: Color,
    val stand: Color,
    val standLight: Color,
) {
    val success: Color get() = mint.solid
    val successContainer: Color get() = mint.container
    val onSuccessContainer: Color get() = mint.content
    val warningContainer: Color get() = pear.container
    val onWarningContainer: Color get() = pear.content
}

/** Spaziature e misure condivise. */
object RicordellaDimensions {
    val spaceXs = 4.dp
    val spaceS = 8.dp
    val spaceM = 12.dp
    val spaceL = 16.dp
    val spaceXl = 24.dp
    val screenPadding = 16.dp
    val cardRadius = 24.dp
    val iconBadge = 44.dp
    val minTouchTarget = 48.dp
    val maxContentWidth = 840.dp
    val pushEdge = 4.dp
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B6FAE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3ECFB),
    onPrimaryContainer = Color(0xFF00344F),
    secondary = Color(0xFF6B5E00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF9E68A),
    onSecondaryContainer = Color(0xFF2B2400),
    tertiary = Color(0xFFC4303F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD9),
    onTertiaryContainer = Color(0xFF40000A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF7EA),
    onBackground = Color(0xFF15181D),
    surface = Color(0xFFFAF7EA),
    onSurface = Color(0xFF15181D),
    surfaceVariant = Color(0xFFEAE5D1),
    onSurfaceVariant = Color(0xFF545A63),
    outline = Color(0xFF7A7766),
    outlineVariant = Color(0xFFD6D0BA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F1E1),
    surfaceContainer = Color(0xFFF0EBD9),
    surfaceContainerHigh = Color(0xFFEAE5D1),
    surfaceContainerHighest = Color(0xFFE4DEC8),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8ED1FF),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF0B4D74),
    onPrimaryContainer = Color(0xFFCDEBFF),
    secondary = Color(0xFFE4C94A),
    onSecondary = Color(0xFF3A3000),
    secondaryContainer = Color(0xFFF2D43D),
    onSecondaryContainer = Color(0xFF1F1A00),
    tertiary = Color(0xFFFFB3B3),
    onTertiary = Color(0xFF680014),
    tertiaryContainer = Color(0xFF8E1427),
    onTertiaryContainer = Color(0xFFFFDAD9),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111319),
    onBackground = Color(0xFFE3E3EA),
    surface = Color(0xFF111319),
    onSurface = Color(0xFFE3E3EA),
    surfaceVariant = Color(0xFF32353E),
    onSurfaceVariant = Color(0xFFB7B9C4),
    outline = Color(0xFF8C8F99),
    outlineVariant = Color(0xFF3F424B),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF1A1C23),
    surfaceContainer = Color(0xFF1E2028),
    surfaceContainerHigh = Color(0xFF282A33),
    surfaceContainerHighest = Color(0xFF33353E),
)

private val LightExtraColors = RicordellaColors(
    bolt = Color(0xFFF2D43D),
    onBolt = Color(0xFF1F1A00),
    boltEdge = Color(0xFFC9A400),
    boltCast = Color(0x66C9A400),
    cyan = Tone(Color(0xFFD3ECFB), Color(0xFF00344F), Color(0xFF1C9BE0)),
    pear = Tone(Color(0xFFFBEEA6), Color(0xFF2B2400), Color(0xFFE2BF12)),
    coral = Tone(Color(0xFFFFDAD9), Color(0xFF5C0012), Color(0xFFF0475B)),
    mint = Tone(Color(0xFFCDF3DA), Color(0xFF00391B), Color(0xFF2FAE67)),
    lavender = Tone(Color(0xFFEBDDFF), Color(0xFF2C0B55), Color(0xFF9B6BE6)),
    glassLight = Color(0xFFEAFAFF),
    glass = Color(0xFF7FD3F7),
    glassDeep = Color(0xFF2A8FD1),
    glassRim = Color(0xFF1B6FA8),
    glassHighlight = Color(0xFFFFFFFF),
    stand = Color(0xFF6A4BD8),
    standLight = Color(0xFF8D73EE),
)

private val DarkExtraColors = RicordellaColors(
    bolt = Color(0xFFF2D43D),
    onBolt = Color(0xFF1F1A00),
    boltEdge = Color(0xFFB08F00),
    boltCast = Color(0x80000000),
    cyan = Tone(Color(0xFF0B4D74), Color(0xFFCDEBFF), Color(0xFF5BC0F5)),
    pear = Tone(Color(0xFF4F4400), Color(0xFFFBEEA6), Color(0xFFF2D43D)),
    coral = Tone(Color(0xFF7A1224), Color(0xFFFFDAD9), Color(0xFFFF7A86)),
    mint = Tone(Color(0xFF14502E), Color(0xFFCDF3DA), Color(0xFF6FD39A)),
    lavender = Tone(Color(0xFF45296F), Color(0xFFEBDDFF), Color(0xFFB98CF0)),
    glassLight = Color(0xFFD9F5FF),
    glass = Color(0xFF5BC0F5),
    glassDeep = Color(0xFF1A6FA8),
    glassRim = Color(0xFF9ADCFF),
    glassHighlight = Color(0xFFFFFFFF),
    stand = Color(0xFF7E62E6),
    standLight = Color(0xFFA48DF2),
)

private fun jakarta(weight: FontWeight) = Font(
    R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Plus Jakarta Sans (OFL), incorporato nell'app: nessun download di font. */
val JakartaSans = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold),
)

private val RicordellaTypography: Typography = Typography().let { base ->
    fun TextStyle.jakarta(weight: FontWeight, tracking: Double? = null) =
        copy(fontFamily = JakartaSans, fontWeight = weight, letterSpacing = tracking?.em ?: letterSpacing)
    base.copy(
        displayLarge = base.displayLarge.jakarta(FontWeight.ExtraBold, -0.025),
        displayMedium = base.displayMedium.jakarta(FontWeight.ExtraBold, -0.025),
        displaySmall = base.displaySmall.jakarta(FontWeight.ExtraBold, -0.02),
        headlineLarge = base.headlineLarge.jakarta(FontWeight.ExtraBold, -0.02),
        headlineMedium = base.headlineMedium.jakarta(FontWeight.ExtraBold, -0.02),
        headlineSmall = base.headlineSmall.jakarta(FontWeight.Bold, -0.015),
        titleLarge = base.titleLarge.jakarta(FontWeight.Bold, -0.01),
        titleMedium = base.titleMedium.jakarta(FontWeight.Bold),
        titleSmall = base.titleSmall.jakarta(FontWeight.Bold),
        bodyLarge = base.bodyLarge.jakarta(FontWeight.Normal),
        bodyMedium = base.bodyMedium.jakarta(FontWeight.Normal),
        bodySmall = base.bodySmall.jakarta(FontWeight.Medium),
        labelLarge = base.labelLarge.jakarta(FontWeight.Bold),
        labelMedium = base.labelMedium.jakarta(FontWeight.Bold),
        labelSmall = base.labelSmall.jakarta(FontWeight.SemiBold),
    )
}

private val RicordellaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(RicordellaDimensions.cardRadius),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalRicordellaColors = staticCompositionLocalOf { LightExtraColors }

@Composable
fun RicordellaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalRicordellaColors provides if (dark) DarkExtraColors else LightExtraColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = RicordellaTypography,
            shapes = RicordellaShapes,
            content = content,
        )
    }
}

/** Accesso comodo ai colori semantici: `MaterialTheme.ricordellaColors`. */
val MaterialTheme.ricordellaColors: RicordellaColors
    @Composable get() = LocalRicordellaColors.current
