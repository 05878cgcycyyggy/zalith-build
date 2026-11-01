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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 惊吓事件当前所处的阶段
 */
enum class JumpscarePhase {
    /** 常态，按键显示原本的文字 */
    Idle,
    /** 所有按键文字被替换为惊吓文本，并显示为红色 */
    Staring,
    /** 屏幕全黑闪烁 */
    Blackout
}

/**
 * 惊吓事件的全局状态
 *
 * 只保存当前阶段这一个最小状态，真正要显示的文字与颜色由读取方通过
 * [rememberJumpscareStyle] 计算，这样可以避免每个按键都各自写一遍判断逻辑。
 */
object JumpscareState {
    /** 惊吓时按键上显示的文字 */
    const val STARE_TEXT = "我看到你了"

    /** 惊吓文字的鲜红色 */
    val STARE_COLOR = Color(0xFFFF1A1A)

    /** 每秒进行一次抽签，抽中的概率 */
    const val TRIGGER_CHANCE = 0.05f

    /** 抽签间隔（毫秒） */
    const val TICK_INTERVAL_MS = 1_000L

    /** 惊吓文字保持时长的可选值（秒） */
    private val STARE_SECONDS_RANGE = 3..4

    /** 黑屏闪烁时长（毫秒） */
    private const val BLACKOUT_DURATION_MS = 1_000L

    /** 当前阶段，Compose 可观察 */
    var phase by mutableStateOf(JumpscarePhase.Idle)
        private set

    /** 是否正在惊吓中（包含黑屏阶段） */
    val isJumpscaring: Boolean
        get() = phase != JumpscarePhase.Idle

    /**
     * 抽签循环
     *
     * 每秒掷一次骰子，命中后依次执行：改写按键文字 -> 黑屏闪烁 -> 恢复常态。
     * 整个流程执行完之前不会再触发下一次，避免事件叠加。
     */
    suspend fun runLoop() {
        while (true) {
            delay(TICK_INTERVAL_MS)
            if (isJumpscaring) continue
            if (Random.nextFloat() >= TRIGGER_CHANCE) continue
            trigger()
        }
    }

    /**
     * 立即触发一次完整的惊吓流程
     */
    private suspend fun trigger() {
        phase = JumpscarePhase.Staring
        delay(STARE_SECONDS_RANGE.random() * 1_000L)

        phase = JumpscarePhase.Blackout
        delay(BLACKOUT_DURATION_MS)

        phase = JumpscarePhase.Idle
    }
}

/**
 * 在应用生命周期内驱动惊吓事件的抽签循环
 *
 * 只在界面处于前台时抽签，退到后台时暂停，回来后重新计时。
 */
@Composable
fun JumpscareEffect() {
    val lifecycleOwner = LocalLifecycleOwner.current

    var foreground by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            foreground = when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_PAUSE -> false
                else -> foreground
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(foreground) {
        if (foreground) {
            JumpscareState.runLoop()
        }
    }
}

/**
 * 惊吓文字样式
 *
 * @property text 按键需要显示的文字，为 null 表示维持原本文字
 * @property color 按键文字需要使用的颜色，为 null 表示维持原本颜色
 */
data class JumpscareStyle(
    val text: String?,
    val color: Color?,
) {
    companion object {
        val Normal = JumpscareStyle(text = null, color = null)
    }
}

/**
 * 读取当前的惊吓样式
 *
 * 只有 [JumpscarePhase.Staring] 阶段才改写文字与颜色，其余阶段一律保持常态。
 */
@Composable
fun rememberJumpscareStyle(): JumpscareStyle {
    return when (JumpscareState.phase) {
        JumpscarePhase.Staring -> JumpscareStyle(
            text = JumpscareState.STARE_TEXT,
            color = JumpscareState.STARE_COLOR
        )
        else -> JumpscareStyle.Normal
    }
}

/**
 * 黑屏闪烁层
 *
 * 铺满整个屏幕，在 [JumpscarePhase.Blackout] 阶段显示为纯黑，用来制造“闪一下”的效果。
 * 使用满不透明度的黑色，不拦截任何触摸事件，闪烁结束后自然消失。
 */
@Composable
fun JumpscareBlackout(modifier: Modifier = Modifier) {
    if (JumpscareState.phase != JumpscarePhase.Blackout) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}
