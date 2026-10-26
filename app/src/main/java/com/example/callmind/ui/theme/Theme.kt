package com.example.callmind.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color.White,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkPrimary,
    secondary = DarkPrimary,
    tertiary = DarkPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerHigh = DarkDialogSurface, // dialogs and bottom sheets
    surfaceContainerHighest = DarkDialogSurface,
    outline = DarkOutline,
    outlineVariant = DarkDivider,
    error = AccentRedDark
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightPrimary,
    secondary = LightPrimary,
    tertiary = LightPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurface,
    outline = LightOutline,
    outlineVariant = LightDivider,
    error = AccentRed
)

/** Card 12 dp, dialogs / sheets 32 dp, as in the reference. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Colour for faint secondary content (inactive tab, chevron). */
val androidx.compose.material3.ColorScheme.tertiaryText: Color
    get() = if (background == DarkBackground) DarkTextTertiary else LightTextTertiary

@Composable
fun CallMindTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val mode by ThemeModeStore.mode.collectAsState()
    // The in-call screen is always dark, like the phone's own.
    val darkTheme = forceDark || when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

/** True for the dark colour scheme (used by screens that draw their own artwork). */
val androidx.compose.material3.ColorScheme.isDark: Boolean get() = background == DarkBackground

/** Green accent used inside the Contacts section. */
val androidx.compose.material3.ColorScheme.contactAccent: Color
    get() = if (isDark) ContactGreenDark else ContactGreenLight

/** Fill of the grey circle behind a contact's initial. */
val androidx.compose.material3.ColorScheme.contactAvatar: Color
    get() = if (isDark) ContactAvatarDark else ContactAvatarLight

/** Soft fill of the search pill. */
val androidx.compose.material3.ColorScheme.searchPill: Color
    get() = if (isDark) Color(0x26FFFFFF) else Color(0x0D000000)

/** The contact list sits on plain white (light) / black (dark); editor and detail pages use the grey page colour. */
val androidx.compose.material3.ColorScheme.contactListBackground: Color
    get() = if (isDark) background else surface
