package com.sprout.focus.widget

import androidx.glance.material3.ColorProviders
import com.sprout.focus.ui.theme.SproutDarkColors
import com.sprout.focus.ui.theme.SproutLightColors

/**
 * Палитра виджета — та же, что у приложения.
 *
 * Виджет живёт на чужом экране, среди чужих иконок, и лаунчер не подставит
 * ему тему приложения. Без явной палитры он взял бы умолчания Material —
 * те самые сиреневые, из-за которых на этапе 0 спорила с зелёной темой
 * нижняя панель.
 */
val SproutGlanceColors = ColorProviders(
    light = SproutLightColors,
    dark = SproutDarkColors,
)
