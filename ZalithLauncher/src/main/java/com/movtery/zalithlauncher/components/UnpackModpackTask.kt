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

package com.movtery.zalithlauncher.components

import android.content.Context
import com.movtery.zalithlauncher.context.copyAssetFile
import com.movtery.zalithlauncher.game.path.getVersionsHome
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.file.extractFromZip
import com.movtery.zalithlauncher.utils.logging.Logger
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "UnpackModpackTask"

/**
 * 首次启动时将内嵌在应用 assets 中的完整游戏实例（ZIP）解压到游戏目录的 versions/ 文件夹下。
 *
 * 该 ZIP 的根目录即为一个完整的 Minecraft 实例（不含 versions/ 前缀），包含版本核心
 * （{版本名}.jar / {版本名}.json）、mods、config、ZalithLauncher/version.config 等。
 * 解压后版本文件夹名必须与 jar/json 文件名一致，版本才能被 [VersionsManager] 自动识别。
 *
 * 由于 assets 目录只读，需先将 ZIP 复制到可写目录，解压完成后删除 ZIP 副本，避免残留。
 */
class UnpackModpackTask(
    private val context: Context
) : AbstractUnpackTask() {

    /** assets 中内嵌 ZIP 的路径 */
    private val modpackAssetPath = "modpack/game-instance.zip"

    /** ZIP 内嵌的版本名称，也是解压后的版本文件夹名（需与 jar/json 文件名一致） */
    private val modpackVersionName = "1.21.1 NeoForge 21.1.264"

    /** 解压后的版本文件夹 */
    private val versionDir: File
        get() = File(getVersionsHome(), modpackVersionName)

    /** 复制 ZIP 到可写目录后的临时副本 */
    private val tempZipFile: File
        get() = File(PathManager.DIR_CACHE_MODPACK_DOWNLOADER, "game-instance.zip")

    private val isCheckFailed: Boolean = runCatching {
        // 预检 assets 中是否存在内嵌 ZIP
        context.assets.open(modpackAssetPath).close()
    }.onFailure { e ->
        Logger.warning(TAG, "Failed to open internal modpack asset. assetsPath=$modpackAssetPath", e)
    }.isFailure

    fun isCheckFailed() = isCheckFailed

    /** 返回内嵌游戏实例的版本名称，用于在解压任务卡片中展示 */
    fun getModpackVersionName(): String = modpackVersionName

    override fun checkState(): InstallableItem.State {
        if (isCheckFailed) return InstallableItem.State.NOT_EXISTS

        return if (isModpackInstalled()) {
            Logger.info(TAG, "Modpack \"$modpackVersionName\" is already installed, continuing...")
            InstallableItem.State.FINISHED
        } else {
            Logger.info(TAG, "Modpack \"$modpackVersionName\" is not installed yet, will unpack it.")
            InstallableItem.State.NOT_STARTED
        }
    }

    /**
     * 判断版本是否已安装完成：版本文件夹存在，且版本核心 jar 与 json 均已就位。
     */
    private fun isModpackInstalled(): Boolean {
        return VersionsManager.isVersionExists(modpackVersionName, checkJson = true)
    }

    override suspend fun run() {
        // 解压前若目标文件夹已存在但未完成，先清空，避免残留旧文件
        runCatching {
            FileUtils.deleteDirectory(versionDir)
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to clean the version directory before unpacking.", e)
        }

        // 1. 将内嵌 ZIP 从只读 assets 复制到可写目录
        updateMessage("copying internal modpack archive")
        context.copyAssetFile(
            fileName = modpackAssetPath,
            output = tempZipFile,
            overwrite = true
        )

        // 2. 解压 ZIP 全部内容到版本文件夹
        updateMessage("unpacking game instance")
        ZipFile(tempZipFile).use { zip ->
            zip.extractFromZip("", versionDir)
        }

        // 3. 解压完成后删除 ZIP 副本，避免残留
        runCatching {
            FileUtils.deleteQuietly(tempZipFile)
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to delete the temporary modpack archive.", e)
        }

        // 4. 刷新版本列表，使该版本显示在主菜单游戏列表中
        VersionsManager.refresh("UnpackModpackTask")

        val fileCount = versionDir.walkTopDown().count { it.isFile }
        Logger.info(TAG, "Modpack \"$modpackVersionName\" unpacked, $fileCount files")
    }
}