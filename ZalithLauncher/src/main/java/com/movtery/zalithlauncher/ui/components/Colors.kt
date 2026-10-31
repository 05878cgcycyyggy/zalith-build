/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.movtery.zalithlauncher.viewmodel.influencedByBackground

/**
 * 降低颜色的饱和度
 */
fun Color.desaturate(factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[1] *= factor.coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * 按键/卡片等元素在有背景时的不透明度
 *
 * 强制背景后为了让背景图最大程度透出，这里取一个远低于原设置项下限（20%）的值，
 * 只保留极淡的一层底色，用来维持卡片和按键的轮廓，避免完全看不见边界。
 */
private const val BACKGROUND_MIN_ALPHA = 0.12f

/**
 * 受背景内容影响的颜色，设置背景内容时，支持调整不透明度用于适配背景内容画面
 * @param influencedAlpha 受影响时，调整的 alpha 值
 */
@Composable
fun influencedByBackgroundColor(
    color: Color,
    influencedAlpha: Float = BACKGROUND_MIN_ALPHA,
    enabled: Boolean = true
): Color {
    return influencedByBackground(
        value = color,
        influenced = color.copy(alpha = influencedAlpha),
        enabled = enabled
    )
}
