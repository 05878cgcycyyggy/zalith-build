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

package com.movtery.zalithlauncher.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movtery.zalithlauncher.context.copyAssetFile
import com.movtery.zalithlauncher.context.GlobalContext
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.enums.BackgroundBlur
import com.movtery.zalithlauncher.utils.image.isImageFile
import com.movtery.zalithlauncher.utils.logging.Logger
import com.movtery.zalithlauncher.utils.video.isVideoFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "BackgroundViewModel"

/**
 * 内置的强制背景图片（位于 assets/background/ 下）
 * 启动器背景被固定为此图，用户无法更改
 */
private const val BUILTIN_BACKGROUND_ASSET = "background/builtin-background.png"

/**
 * 强制背景时使用的不透明度
 * 100 会让背景被完全不透明的遮罩盖住，因此固定在 80 保证背景可见
 */
private const val BG_FORCED_OPACITY = 80

/**
 * 启动器背景管理
 */
class BackgroundViewModel: ViewModel() {
    val backgroundFile: File = PathManager.FILE_LAUNCHER_BACKGROUND

    /**
     * 背景文件是否有效（存在，且是有效的视频或图片）
     */
    var isValid by mutableStateOf(false)
        private set

    /**
     * 背景文件是一个视频
     */
    var isVideo by mutableStateOf(false)
        private set

    /**
     * 背景文件是一个图片
     */
    var isImage by mutableStateOf(false)
        private set

    var refreshTrigger by mutableStateOf(false)
        private set

    /**
     * 背景容器在屏幕坐标系中的区域，由背景捕获节点在布局时记录；
     * 玻璃模糊层依据它与自身位置对齐采样模糊位图
     */
    var backgroundBounds by mutableStateOf(Rect.Zero)
        private set

    private val glassBlur = BackgroundGlassBlur(backgroundFile)

    /**
     * Android 12+ 上由背景捕获节点维护的共享预模糊 GPU 层
     */
    val glassLayer: GraphicsLayer? get() = glassBlur.glassLayer

    /**
     * Android 12- 上由背景捕获节点维护的清晰捕获层
     */
    val captureLayer: GraphicsLayer? get() = glassBlur.captureLayer

    /**
     * 预模糊的背景位图 (Android 12-)
     */
    val blurredBackground: ImageBitmap? get() = glassBlur.blurredBackground

    fun updateBackgroundBounds(coordinates: LayoutCoordinates) {
        val position = coordinates.positionOnScreen()
        if (!position.isSpecified) return
        backgroundBounds = Rect(position, coordinates.size.toSize())
    }

    fun attachGlassLayer(layer: GraphicsLayer?) {
        glassBlur.attachGlassLayer(layer)
    }

    fun attachCaptureLayer(layer: GraphicsLayer?) {
        glassBlur.attachCaptureLayer(layer)
    }

    /**
     * 捕获当前捕获层的一帧并模糊为前景毛玻璃位图 (Android 12-)
     */
    suspend fun captureGlassFrame() {
        glassBlur.captureFrame()
    }

    private suspend fun updateState() {
        val (isImage0, isVideo0) = withContext(Dispatchers.IO) {
            val isImage = backgroundFile.isImageFile()
            //如果文件是图片，则不检查是否为视频
            val isVideo = if (!isImage) backgroundFile.isVideoFile() else false
            isImage to isVideo
        }
        //更新状态
        withContext(Dispatchers.Main) {
            isImage = isImage0
            isVideo = isVideo0
            isValid = backgroundFile.exists() && (isImage0 || isVideo0)
            refreshTrigger = !refreshTrigger
        }
    }

    init {
        //【强制背景】启动器背景被固定为内置图片，不允许用户更改
        //每次创建 ViewModel 都从 assets 重新覆盖一次，确保背景文件始终是这张图
        viewModelScope.launch(Dispatchers.IO) {
            ensureBuiltinBackground()
            updateState()
        }

        //【强制背景】校正背景显示参数，确保这张图片一定可见
        //不透明度 >= 100 会让背景完全被遮罩盖住，这里强制限制在可见范围
        if (AllSettings.launcherBackgroundOpacity.state >= 100) {
            AllSettings.launcherBackgroundOpacity.save(BG_FORCED_OPACITY)
        }
        //模糊值为 0 才是清晰原图，避免背景被糊涂
        if (AllSettings.backgroundBlur.state != 0) {
            AllSettings.backgroundBlur.save(0)
        }

        viewModelScope.launch {
            snapshotFlow {
                BlurConfig(
                    blur = AllSettings.backgroundBlur.state,
                    blurType = AllSettings.backgroundBlurType.state,
                    opacity = AllSettings.launcherBackgroundOpacity.state,
                    valid = isValid,
                    isImage = isImage,
                    generation = refreshTrigger
                )
            }.collectLatest { config ->
                when {
                    Build.VERSION.SDK_INT >= 31 || !config.valid ||
                        config.blur <= 0 || config.opacity >= 100 ->
                        glassBlur.clearBlurredBackground()
                    config.isImage && config.blurType == BackgroundBlur.Background ->
                        glassBlur.refreshStaticDisplayBitmap(config.blur)
                }
            }
        }
    }

    suspend fun delete() {
        //【强制背景】背景被固定为内置图片，禁止删除
        //这里不执行任何删除操作，仅重新同步一次状态，保证背景文件存在
        withContext(Dispatchers.IO) {
            ensureBuiltinBackground()
            updateState()
        }
    }

    suspend fun import(context: Context, result: Uri) {
        //【强制背景】背景被固定为内置图片，禁止用户更换
        //忽略用户选择的文件，直接恢复为内置背景
        withContext(Dispatchers.IO) {
            Logger.warning(TAG, "Changing the launcher background is disabled; restoring the builtin background.")
            ensureBuiltinBackground()
            updateState()
        }
    }

    /**
     * 确保内置背景文件存在于磁盘上，内容始终与 assets 中的图片一致
     * 无条件覆盖，保证用户无法通过任何方式替换背景
     */
    private fun ensureBuiltinBackground() {
        try {
            GlobalContext.copyAssetFile(
                fileName = BUILTIN_BACKGROUND_ASSET,
                output = backgroundFile,
                overwrite = true
            )
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to install the builtin launcher background.", e)
        }
    }
}

/**
 * 生成模糊位图的触发条件快照，任一变化都会重新生成
 */
private data class BlurConfig(
    val blur: Int,
    val blurType: BackgroundBlur,
    val opacity: Int,
    val valid: Boolean,
    val isImage: Boolean,
    val generation: Boolean
)

/**
 * 本地随时可以使用的背景图片管理 ViewModel
 * 由 MainActivity 的主题提供
 */
val LocalBackgroundViewModel = compositionLocalOf<BackgroundViewModel?> {
    error("No BackgroundViewModel provided")
}

@Composable
fun <E> influencedByBackground(
    value: E,
    influenced: E,
    enabled: Boolean = true
): E {
    return if (enabled) {
        if (LocalBackgroundViewModel.current?.isValid == true) {
            influenced
        } else {
            value
        }
    } else {
        value
    }
}

@Composable
fun backgroundVisible(): Boolean {
    return LocalBackgroundViewModel.current?.isValid == true && AllSettings.launcherBackgroundOpacity.state < 100
}