package jadx.gui.utils.ui

import jadx.api.ResourcesLoader
import jadx.core.plugins.files.TempFilesGetter
import jadx.gui.treemodel.JResource
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.Frame
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 资源文件导出与系统打开工具。
 *
 * **做什么**：[exportBinary] 把资源内容解码并写入指定路径；
 * [openFile] 先导出到临时目录，再调用系统默认程序打开。
 *
 * **为什么用 `companion object` + `@JvmStatic`**：被 Java（`MainWindow`）与 Kotlin 同时调用，
 * 保持静态方法 JVM 表面不变。
 */
class FileOpenerHelper {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(FileOpenerHelper::class.java)

		/** 把资源内容解码后写入 [savePath]。 */
		fun exportBinary(resource: JResource, savePath: Path) {
			try {
				BufferedOutputStream(FileOutputStream(savePath.toFile())).use { os ->
					var bytes: ByteArray? = ResourcesLoader.decodeStream(checkNotNull(resource.getResFile())) { _, is_ ->
						is_.readAllBytes()
					}
					if (bytes == null) {
						bytes = ByteArray(0)
					}
					os.write(bytes)
				}
			} catch (e: Exception) {
				throw RuntimeException("Error saving file " + resource.getName(), e)
			}
		}

		/** 导出资源到临时目录并用系统默认程序打开。 */
		fun openFile(frame: Frame, res: JResource) {
			if (Desktop.isDesktopSupported()) {
				val desktop = Desktop.getDesktop()
				val tempDir = TempFilesGetter.INSTANCE.getTempDir()
				val resFile = checkNotNull(res.getResFile())
				val path = Paths.get(resFile.getDeobfName())
				val fileNamePath = path.getFileName()
				val filePath = tempDir.resolve(fileNamePath)
				exportBinary(res, filePath)

				if (!Files.exists(filePath)) {
					UiUtils.errorMessage(frame, NLS.str("error_dialog.not_found_file", filePath))
					return
				}
				if (Files.isDirectory(filePath)) {
					UiUtils.errorMessage(frame, NLS.str("error_dialog.path_is_directory", filePath))
					return
				}
				if (!Files.isReadable(filePath)) {
					UiUtils.errorMessage(frame, NLS.str("error_dialog.cannot_read", filePath))
					return
				}

				try {
					desktop.open(filePath.toFile())
				} catch (ex: IOException) {
					UiUtils.errorMessage(frame, NLS.str("error_dialog.open_failed", ex.message))
					LOG.error("Unable to open file: {0}", ex)
				} catch (ex: IllegalArgumentException) {
					UiUtils.errorMessage(frame, NLS.str("error_dialog.invalid_path_format", ex.message))
					LOG.error("Invalid file path: {0}", ex)
				}
			} else {
				UiUtils.errorMessage(frame, NLS.str("error_dialog.desktop_unsupported"))
			}
		}
	}
}
