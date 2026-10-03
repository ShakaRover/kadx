package jadx.gui.utils

import jadx.core.export.TemplateFile
import jadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Linux 桌面快捷方式（.desktop 文件 + 图标）安装工具。
 *
 * **做什么**：调用 `xdg-desktop-menu` / `xdg-icon-resource` 把 jadx-gui 注册到桌面菜单。
 *
 * **为什么用 `object`**：原 Java 全部是静态方法，`object` + `@JvmStatic`
 * 保持 Java 侧 `DesktopEntryUtils.xxx()` 调用不变。
 */
object DesktopEntryUtils {

	private val LOG: Logger = LoggerFactory.getLogger(DesktopEntryUtils::class.java)

	/** 需要安装的图标尺寸 -> 资源文件名。 */
	private val SIZE_TO_LOGO_MAP: Map<Int, String> = mapOf(
		16 to "jadx-logo-16px.png",
		32 to "jadx-logo-32px.png",
		48 to "jadx-logo-48px.png",
		252 to "jadx-logo.png",
		256 to "jadx-logo.png",
	)

	private val XDG_DESKTOP_MENU_COMMAND_PATH: Path? = findExecutablePath("xdg-desktop-menu")
	private val XDG_ICON_RESOURCE_COMMAND_PATH: Path? = findExecutablePath("xdg-icon-resource")

	fun createDesktopEntry(): Boolean {
		if (XDG_DESKTOP_MENU_COMMAND_PATH == null) {
			LOG.error("xdg-desktop-menu was not found in \$PATH")
			return false
		}
		if (XDG_ICON_RESOURCE_COMMAND_PATH == null) {
			LOG.error("xdg-icon-resource was not found in \$PATH")
			return false
		}
		val desktopTempFile = FileUtils.createTempFileNonPrefixed("jadx-gui.desktop")
		val iconTempFolder = FileUtils.createTempDir("logos")
		LOG.debug("Creating desktop with temp files: {}, {}", desktopTempFile, iconTempFolder)
		try {
			return createDesktopEntry(desktopTempFile, iconTempFolder)
		} finally {
			try {
				FileUtils.deleteFileIfExists(desktopTempFile)
				FileUtils.deleteDirIfExists(iconTempFolder)
			} catch (e: IOException) {
				LOG.error("Failed to clean up temp files", e)
			}
		}
	}

	private fun createDesktopEntry(desktopTempFile: Path, iconTempFolder: Path): Boolean {
		val launchScriptPath = getLaunchScriptPath() ?: return false
		for ((size, logo) in SIZE_TO_LOGO_MAP) {
			val path = iconTempFolder.resolve("$size.png")
			if (!writeLogoFile(logo, path)) {
				return false
			}
			if (!installIcon(size, path)) {
				return false
			}
		}
		if (!writeDesktopFile(launchScriptPath, desktopTempFile)) {
			return false
		}
		return installDesktopEntry(desktopTempFile)
	}

	private fun installDesktopEntry(desktopTempFile: Path): Boolean {
		try {
			val desktopFileInstallCommand = ProcessBuilder(
				checkNotNull(XDG_DESKTOP_MENU_COMMAND_PATH).toString(),
				"install",
				desktopTempFile.toString(),
			)
			val process = desktopFileInstallCommand.start()
			val statusCode = process.waitFor()
			if (statusCode != 0) {
				LOG.error("Got error code {} while installing desktop file", statusCode)
				return false
			}
		} catch (e: Exception) {
			LOG.error("Failed to install desktop file", e)
			return false
		}
		LOG.info("Successfully installed desktop file")
		return true
	}

	private fun installIcon(size: Int, iconPath: Path): Boolean {
		try {
			val iconInstallCommand = ProcessBuilder(
				checkNotNull(XDG_ICON_RESOURCE_COMMAND_PATH).toString(),
				"install",
				"--novendor",
				"--size",
				size.toString(),
				iconPath.toString(),
				"jadx",
			)
			val process = iconInstallCommand.start()
			val statusCode = process.waitFor()
			if (statusCode != 0) {
				LOG.error("Got error code {} while installing icon of size {}", statusCode, size)
				return false
			}
		} catch (e: Exception) {
			LOG.error("Failed to install icon of size {}", size, e)
			return false
		}
		LOG.info("Successfully installed icon of size {}", size)
		return true
	}

	/** 在 `$PATH` 中查找可执行文件；找不到返回 null。 */
	private fun findExecutablePath(executableName: String): Path? {
		val pathEnv = System.getenv("PATH") ?: return null
		for (pathDirectory in pathEnv.split(File.pathSeparator)) {
			val path = Paths.get(pathDirectory, executableName)
			if (path.toFile().isFile && path.toFile().canExecute()) {
				return path
			}
		}
		return null
	}

	private fun writeDesktopFile(launchScriptPath: String, desktopFilePath: Path): Boolean {
		try {
			val tmpl = TemplateFile.fromResources("/files/jadx-gui.desktop.tmpl")
			tmpl.add("launchScriptPath", launchScriptPath)
			FileUtils.writeFile(desktopFilePath, tmpl.build())
		} catch (e: Exception) {
			LOG.error("Failed to save .desktop file at: {}", desktopFilePath, e)
			return false
		}
		LOG.debug("Wrote .desktop file to {}", desktopFilePath)
		return true
	}

	private fun writeLogoFile(logoFile: String, logoPath: Path): Boolean {
		try {
			DesktopEntryUtils::class.java.getResourceAsStream("/logos/$logoFile").use { stream ->
				FileUtils.writeFile(logoPath, stream)
			}
		} catch (e: Exception) {
			LOG.error("Failed to write logo file at: {}", logoPath, e)
			return false
		}
		LOG.debug("Wrote logo file to: {}", logoPath)
		return true
	}

	/** 获取 jadx 启动脚本路径（由 `jadx.launchScript.path` 系统属性提供）。 */
	fun getLaunchScriptPath(): String? {
		val launchScriptPath = System.getProperty("jadx.launchScript.path")
		if (launchScriptPath == null || launchScriptPath.isEmpty()) {
			LOG.error(
				"The jadx.launchScript.path property is not set. Please launch JADX with the bundled launch script or set it to the appropriate value yourself.",
			)
			return null
		}
		LOG.debug("JADX launch script path: {}", launchScriptPath)
		return launchScriptPath
	}
}
