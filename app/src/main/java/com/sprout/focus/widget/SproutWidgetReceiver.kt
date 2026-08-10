package com.sprout.focus.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Через этот получатель система разговаривает с виджетом: просит нарисовать
 * себя, сообщает о добавлении на экран и об удалении.
 */
class SproutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget get() = SproutWidget
}
