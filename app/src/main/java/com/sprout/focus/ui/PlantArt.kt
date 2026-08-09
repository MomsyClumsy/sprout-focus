package com.sprout.focus.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sprout.focus.R

/**
 * Растение по стадии роста.
 *
 * 0 — семя, 1 — росток, 2 — побег, 3 — растение с бутоном, 4 — цветение.
 * Пока ни одной сессии не было, честно показываем семя: ещё ничего
 * не выросло, и это нормально.
 */
@Composable
fun PlantArt(stage: Int, size: Dp = 120.dp, modifier: Modifier = Modifier) {
    val res = when (stage.coerceIn(0, 4)) {
        0 -> R.drawable.plant_stage_0
        1 -> R.drawable.plant_stage_1
        2 -> R.drawable.plant_stage_2
        3 -> R.drawable.plant_stage_3
        else -> R.drawable.plant_stage_4
    }
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
