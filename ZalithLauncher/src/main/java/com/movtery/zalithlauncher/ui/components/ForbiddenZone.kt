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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 禁地彩蛋
 *
 * 下载页在主菜单里的入口已经被隐藏，但页面本身的路由仍然注册着。
 * 一旦有玩家用特殊方式闯进来，就把整页文字替换掉，并在几秒后让启动器崩溃。
 *
 * 这里用 CompositionLocal 而不是全局单例，因为禁用范围必须严格限定在下载页内部，
 * 下载页以外的任何界面都不能受影响。
 */

/** 闯入者看到的替换文字 */
const val FORBIDDEN_TEXT = "你不应该来到这里"

/** 闯入文字使用的鲜红色 */
val FORBIDDEN_COLOR = Color(0xFFFF1A1A)

/** 从闯入到崩溃之间的等待时间（毫秒） */
const val FORBIDDEN_COUNTDOWN_MS = 3_000L

/**
 * 当前界面是否处于「禁地」状态
 *
 * 下载页在根部提供 true，页面内所有文字渲染点读取它来决定是否替换文字。
 * 默认 false，因此下载页以外的一切界面都不受影响。
 */
val LocalForbiddenZone = staticCompositionLocalOf { false }

/**
 * 崩溃报告里使用的固定标识
 *
 * 崩溃页依靠这个标识判断「这次崩溃是不是闯进禁地导致的」，
 * 只有命中时才把整页换成日文并锁死重启按钮。
 */
const val FORBIDDEN_CRASH_MARK = "ZalithForbiddenZoneIntrusion"

/**
 * 构造一次「闯入禁地」造成的崩溃
 *
 * 这不是一个被抛出的异常，而是被当作一条真实的崩溃记录来使用：
 * 内容会原样写进崩溃日志，同时被崩溃页识别出来，换成专属的日文界面。
 * 之所以不在主线程抛异常，是因为全局处理器在显示崩溃页之后会直接杀掉进程，
 * 那属于「自己杀自己」，而这里需要的是让应用自己「崩」掉，进程仍在。
 */
fun forbiddenCrash(): Throwable {
    return IllegalStateException(
        "$FORBIDDEN_CRASH_MARK: 检测到未经授权的访问路径，下载模块已自行终止"
    )
}

/**
 * 崩溃页要展示的日文文本
 *
 * 反复堆叠同一句话，铺满整个内容区，制造「页面上只剩下这一句」的压迫感。
 *
 * @param lines 需要输出的行数
 */
fun forbiddenCrashBody(lines: Int = 200): String {
    val sentence = "ここに来てはいけない"
    return buildString {
        repeat(lines) { index ->
            if (index > 0) append('\n')
            append(sentence)
        }
    }
}

/**
 * 判断崩溃页当前的异常是否来自禁地闯入
 */
fun isForbiddenCrash(throwable: Throwable): Boolean {
    return throwable.message?.contains(FORBIDDEN_CRASH_MARK) == true ||
            throwable.cause?.message?.contains(FORBIDDEN_CRASH_MARK) == true
}

/**
 * 禁地覆盖层
 *
 * 下载页里的文字渲染点太多（分类标签、列表项、搜索框、按钮……），逐个改写既容易漏
 * 也不好维护。这里改为在整页之上压一层不透明遮挡，再把警告文字铺满整个屏幕，
 * 效果就是「页面上的每一个字都变成了这一句」。
 */
@Composable
fun ForbiddenOverlay(modifier: Modifier = Modifier) {
    if (!LocalForbiddenZone.current) return

    Box(
        modifier = modifier.background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(22) {
                Text(
                    text = FORBIDDEN_TEXT,
                    color = FORBIDDEN_COLOR,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
    }
}
