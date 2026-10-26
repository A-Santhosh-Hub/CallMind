package com.example.callmind.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Design tokens. Values follow the "Phone management" (OriginUI VOS 7.0) reference:
 * flat page colour, cards one step lighter (light) or lighter-than-black (dark), one blue accent,
 * text in graded opacities rather than separate greys, hairline dividers.
 */

// ---- Light ----
val LightBackground = Color(0xFFF2F2F5)        // page
val LightSurface = Color(0xFFFFFFFF)           // cards, bars, dialogs
val LightSurfaceVariant = Color(0xFFF2F2F5)    // soft fills inside cards (avatars, chips)
val LightTextPrimary = Color(0xE6000000)       // 90 % black
val LightTextSecondary = Color(0x85000000)     // 52 % black
val LightTextTertiary = Color(0x4D000000)      // 30 % black (inactive tabs, chevrons)
val LightDivider = Color(0x0D000000)           // 5 % black
val LightOutline = Color(0x1F000000)           // button / field strokes
val LightPrimary = Color(0xFF0F77FF)
val LightPrimaryContainer = Color(0xFFE7F1FF)  // 10 % blue over white

// ---- Dark ----
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF202020)
val DarkSurfaceVariant = Color(0xFF2B2B2B)
val DarkDialogSurface = Color(0xFF282828)
val DarkTextPrimary = Color(0xE6FFFFFF)
val DarkTextSecondary = Color(0xA6FFFFFF)
val DarkTextTertiary = Color(0x73FFFFFF)
val DarkDivider = Color(0x26FFFFFF)
val DarkOutline = Color(0x4DFFFFFF)
val DarkPrimary = Color(0xFF2785FF)
val DarkPrimaryContainer = Color(0xFF1F344D)   // 20 % blue over card grey

// ---- Status / accents (action buttons, call types) ----
val AccentGreen = Color(0xFF18CA7F)
val AccentWhatsApp = Color(0xFF25D366)
val AccentTelegram = Color(0xFF0088CC)
val AccentSchedule = Color(0xFFF9A825)
val AccentNotes = Color(0xFF7B1FA2)
val AccentRed = Color(0xFFED4D47)
val AccentRedDark = Color(0xFFEF5F5A)

// ---- Contacts section accent (the reference Phone/Contacts app uses green, not blue) ----
val ContactGreenLight = Color(0xFF32BF55)
val ContactGreenDark = Color(0xFF47C666)
val ContactAvatarLight = Color(0xFFEBEBEB)       // initial-letter circle
val ContactAvatarDark = Color(0xFF383838)
