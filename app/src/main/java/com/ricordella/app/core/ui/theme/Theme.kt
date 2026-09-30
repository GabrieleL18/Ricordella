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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ricordella.app.domain.model.ThemeMode

/** Colori semantici aggiuntivi rispetto a Material (stati positivi e di avviso). */
@Immutable
data class RicordellaColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

/** Spaziature e misure condivise. */
object RicordellaDimensions {
    val spaceXs = 4.dp
    val spaceS = 8.dp
    val spaceM = 12.dp
    val spaceL = 16.dp
    val spaceXl = 24.dp
    val screenPadding = 16.dp
    val cardRadius = 20.dp
    val iconBadge = 40.dp
    val minTouchTarget = 48.dp
    val maxContentWidth = 840.dp
}

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF4F46B8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3DFFF),
    onPrimaryContainer = Color(0xFF140A5E),
    secondary = Color(0xFF5E5C71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4DFF9),
    onSecondaryContainer = Color(0xFF1B192C),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1C1B20),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1C1B20),
    surfaceVariant = Color(0xFFE5E0EC),
    onSurfaceVariant = Color(0xFF48454E),
    outline = Color(0xFF79757F),
    outlineVariant = Color(0xFFC9C5D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2FA),
    surfaceContainer = Color(0xFFF0ECF4),
    surfaceContainerHigh = Color(0xFFEBE6EE),
    surfaceContainerHighest = Color(0xFFE5E1E9),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFC5C0FF),
    onPrimary = Color(0xFF2A1F8F),
    primaryContainer = Color(0xFF3D34A0),
    onPrimaryContainer = Color(0xFFE3DFFF),
    secondary = Color(0xFFC8C3DC),
    onSecondary = Color(0xFF302E41),
    secondaryContainer = Color(0xFF47455A),
    onSecondaryContainer = Color(0xFFE4DFF9),
    tertiary = Color(0xFFFFB870),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3C00),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF48454E),
    onSurfaceVariant = Color(0xFFC9C5D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF48454E),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1C1B20),
    surfaceContainer = Color(0xFF201F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
)

private val LightExtraColors = RicordellaColors(
    success = Color(0xFF2E7D32),
    successContainer = Color(0xFFC8F0C6),
    onSuccessContainer = Color(0xFF002204),
    warning = Color(0xFF8A5100),
    warningContainer = Color(0xFFFFDCBE),
    onWarningContainer = Color(0xFF2C1600),
)

private val DarkExtraColors = RicordellaColors(
    success = Color(0xFF8BD68F),
    successContainer = Color(0xFF14521A),
    onSuccessContainer = Color(0xFFC8F0C6),
    warning = Color(0xFFFFB870),
    warningContainer = Color(0xFF6A3C00),
    onWarningContainer = Color(0xFFFFDCBE),
)

private val RicordellaTypography: Typography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val RicordellaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(RicordellaDimensions.cardRadius),
    extraLarge = RoundedCornerShape(28.dp),
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
