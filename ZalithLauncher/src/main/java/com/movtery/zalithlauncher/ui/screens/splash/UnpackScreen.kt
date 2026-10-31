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

package com.movtery.zalithlauncher.ui.screens.splash

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.components.InstallableItem
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.components.MarqueeText
import com.movtery.zalithlauncher.ui.components.ScalingActionButton
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.theme.itemColor
import com.movtery.zalithlauncher.ui.theme.onItemColor
import com.movtery.zalithlauncher.utils.animation.getAnimateTween
import com.movtery.zalithlauncher.utils.animation.swapAnimateDpAsState
import com.movtery.zalithlauncher.viewmodel.SplashBackStackViewModel

@Composable
fun UnpackScreen(
    items: List<InstallableItem>,
    screenViewModel: SplashBackStackViewModel,
    onAgreeClick: () -> Unit = {}
) {
    BaseScreen(
        screenKey = NormalNavKey.UnpackDeps,
        currentKey = screenViewModel.splashScreen.currentKey
    ) { isVisible ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UnpackTaskList(
                isVisible = isVisible,
                items = items,
                modifier = Modifier
                    .weight(7f)
                    .fillMaxHeight()
            )

            ActionMenu(
                isVisible = isVisible,
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight(),
                onAgreeClick = onAgreeClick
            )
        }
    }
}

@Composable
private fun ActionMenu(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    onAgreeClick: () -> Unit = {}
) {
    var installing by remember { mutableStateOf(false) }

    //【按钮脱落动画】点击“同意并安装”后，按钮以最右侧为锚点向下摆动，最后脱落坠出屏幕
    //锚点在按钮最右侧，负角度 = 按钮左端向下方落下（钟摆方向）
    //仅为视觉效果，安装/解压逻辑完全不受影响
    var dropStarted by remember { mutableStateOf(false) }

    //摆动角度：以最右侧为轴心，左端先向下摆到垂直（半圈），
    //然后像钟摆一样反复回摆，幅度逐次衰减，最后甩到垂直位置脱开
    val swingAngle by animateFloatAsState(
        targetValue = if (dropStarted) -90f else 0f,
        animationSpec = keyframes {
            durationMillis = 1500
            //第一次向下摆到底：左端落下 90°，按钮立起来
            -90f at 380
            //第一次回摆，幅度约为原来的 3/4
            -24f at 600
            //第二次下摆，幅度略小于第一次
            -78f at 810
            //第二次回摆
            -40f at 970
            //第三次下摆
            -70f at 1110
            //第三次回摆，幅度明显缩小
            -52f at 1230
            //第四次下摆
            -66f at 1330
            //最后一次回摆，几乎贴着垂直位置
            -58f at 1400
            //甩到垂直位置，此时失去支撑开始坠落
            -90f at 1500
        },
        label = "AgreeButtonSwing"
    )

    //坠落：摆动结束的同时立刻坠落，不在垂直位置悬停
    val dropProgress by animateFloatAsState(
        targetValue = if (dropStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = 620,
            delayMillis = 1500,
            easing = CubicBezierEasing(0.55f, 0f, 1f, 1f)
        ),
        label = "AgreeButtonDrop"
    )

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val xOffset by swapAnimateDpAsState(
        targetValue = 40.dp,
        swapIn = isVisible,
        isHorizontal = true
    )

    Column(
        modifier = modifier.offset { IntOffset(x = xOffset.roundToPx(), y = 0) },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(state = rememberScrollState())
                .weight(1f)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = if (installing) {
                    stringResource(R.string.splash_screen_installing)
                } else {
                    stringResource(R.string.splash_screen_unpack_desc)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        ScalingActionButton(
            //保持原有配色，避免点击瞬间变灰打断旋转脱落的观感
            enabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    //锚点：按钮最右侧、垂直中点（最右边第一个锚点）
                    transformOrigin = TransformOrigin(1f, 0.5f)
                    //绕锚点摆动：负角度让按钮左端向下方落下
                    rotationZ = swingAngle
                    //摆动结束后失去支撑，加速坠出屏幕底部
                    translationY = screenHeightPx * 1.5f * dropProgress
                    //坠落过程中淡出，避免边缘残留
                    alpha = 1f - dropProgress
                },
            onClick = {
                if (!installing) {
                    installing = true
                    dropStarted = true
                    //动画只是视觉效果，安装/解压立即正常开始
                    onAgreeClick()
                }
            }
        ) {
            MarqueeText(text = stringResource(R.string.splash_screen_agree))
        }
    }
}

@Composable
private fun UnpackTaskList(
    isVisible: Boolean,
    items: List<InstallableItem>,
    modifier: Modifier = Modifier,
) {
    val yOffset by swapAnimateDpAsState(
        targetValue = (-40).dp,
        swapIn = isVisible
    )

    BackgroundCard(
        modifier = modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
        influencedByBackground = false,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        val scrollState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nonInteractiveScrollbar(
                    state = scrollState.scrollIndicatorState!!,
                    orientation = Orientation.Vertical,
                ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            state = scrollState,
        ) {
            items(items) { item ->
                TaskItem(
                    item = item,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TaskItem(
    item: InstallableItem,
    modifier: Modifier = Modifier
) {
    val state by item.state.collectAsStateWithLifecycle()
    val message by item.task.taskMessage.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = itemColor(),
        contentColor = onItemColor(),
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    .animateContentSize(animationSpec = getAnimateTween())
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelMedium
                )
                item.summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (state == InstallableItem.State.RUNNING) {
                    message?.let { taskMessage ->
                        Text(
                            text = taskMessage,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }

            val iconModifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp))
                .size(18.dp)
            when (state) {
                InstallableItem.State.NOT_STARTED -> {
                    Icon(
                        modifier = iconModifier,
                        painter = painterResource(R.drawable.ic_folder_zip_outlined),
                        contentDescription = null
                    )
                }
                InstallableItem.State.PENDING -> {
                    Icon(
                        modifier = iconModifier,
                        painter = painterResource(R.drawable.ic_update),
                        contentDescription = null
                    )
                }
                InstallableItem.State.RUNNING -> {
                    CircularProgressIndicator(
                        modifier = iconModifier,
                        strokeWidth = 2.dp
                    )
                }
                InstallableItem.State.FINISHED -> {
                    Icon(
                        modifier = iconModifier,
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null
                    )
                }
                else -> {}
            }
        }
    }
}