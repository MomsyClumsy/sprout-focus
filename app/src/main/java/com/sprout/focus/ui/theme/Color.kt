package com.sprout.focus.ui.theme

import androidx.compose.ui.graphics.Color

// Палитра Sprout. Зелёная, спокойная, без кричащих акцентов —
// приложение должно успокаивать, а не подгонять.

// --- Светлая тема ---
val LightPrimary = Color(0xFF3D6B3D)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFBFF0BC)
val LightOnPrimaryContainer = Color(0xFF002105)

val LightSecondary = Color(0xFF52634F)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD5E8CF)
val LightOnSecondaryContainer = Color(0xFF101F10)

val LightTertiary = Color(0xFF38656A)
val LightOnTertiary = Color(0xFFFFFFFF)

val LightBackground = Color(0xFFF7FBF2)
val LightOnBackground = Color(0xFF191D17)
val LightSurface = Color(0xFFF7FBF2)
val LightOnSurface = Color(0xFF191D17)
val LightSurfaceVariant = Color(0xFFDEE5D9)
val LightOnSurfaceVariant = Color(0xFF424940)
val LightOutline = Color(0xFF72796F)

// Роли контейнеров поверхности. Их использует нижняя панель, карточки и меню.
// Если их не задать, Material подставит свои сиреневые оттенки и панель
// будет спорить с зелёной палитрой.
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF1F5EC)
val LightSurfaceContainer = Color(0xFFEBEFE6)
val LightSurfaceContainerHigh = Color(0xFFE5EAE0)
val LightSurfaceContainerHighest = Color(0xFFDFE4DA)

// Ошибки — приглушённая терракота вместо стандартного насыщенного красного.
// В исследованиях красный даёт самый высокий балл тревоги, а приложение
// про тревожное избегание не должно бить этим сигналом.
val LightError = Color(0xFF8F4A3E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD2)
val LightOnErrorContainer = Color(0xFF3A0B03)

// --- Тёмная тема ---
val DarkPrimary = Color(0xFFA4D4A2)
val DarkOnPrimary = Color(0xFF0B3911)
val DarkPrimaryContainer = Color(0xFF255127)
val DarkOnPrimaryContainer = Color(0xFFBFF0BC)

val DarkSecondary = Color(0xFFB9CCB4)
val DarkOnSecondary = Color(0xFF243424)
val DarkSecondaryContainer = Color(0xFF3A4B39)
val DarkOnSecondaryContainer = Color(0xFFD5E8CF)

val DarkTertiary = Color(0xFFA0CFD4)
val DarkOnTertiary = Color(0xFF00363B)

val DarkBackground = Color(0xFF11140F)
val DarkOnBackground = Color(0xFFE1E4DB)
val DarkSurface = Color(0xFF11140F)
val DarkOnSurface = Color(0xFFE1E4DB)
val DarkSurfaceVariant = Color(0xFF424940)
val DarkOnSurfaceVariant = Color(0xFFC2C9BD)
val DarkOutline = Color(0xFF8C9388)

val DarkSurfaceContainerLowest = Color(0xFF0C0F0A)
val DarkSurfaceContainerLow = Color(0xFF191D17)
val DarkSurfaceContainer = Color(0xFF1D211B)
val DarkSurfaceContainerHigh = Color(0xFF282C25)
val DarkSurfaceContainerHighest = Color(0xFF333730)

val DarkError = Color(0xFFE7B0A2)
val DarkOnError = Color(0xFF4A1409)
val DarkErrorContainer = Color(0xFF6B3227)
val DarkOnErrorContainer = Color(0xFFFFDAD2)
