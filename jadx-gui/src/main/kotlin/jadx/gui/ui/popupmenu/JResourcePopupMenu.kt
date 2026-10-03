package jadx.gui.ui.popupmenu

import jadx.api.ResourceType
import jadx.api.plugins.utils.CommonFileUtils
import jadx.core.dex.visitors.SaveCode
import jadx.gui.treemodel.JResource
import jadx.gui.ui.MainWindow
import jadx.gui.ui.filedialog.FileDialogWrapper
import jadx.gui.ui.filedialog.FileOpenMode
import jadx.gui.utils.NLS
import jadx.gui.utils.ui.FileOpenerHelper
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * 资源节点的右键菜单。
 *
 * **做什么**：提供「导出」菜单项；根节点不显示导出。目录会递归导出，
 * 文件则根据资源类型以文本或二进制方式写出。
 *
 * **线程模型**：菜单动作在 EDT 上执行，导出为同步文件写入。
 */
class JResourcePopupMenu(private val mainWindow: MainWindow, resource: JResource) : JPopupMenu() {

	init {
		if (resource.getType() != JResource.JResType.ROOT) {
			add(makeExportMenuItem(resource))
		}
	}

	private fun makeExportMenuItem(resource: JResource): JMenuItem {
		val exportMenu = JMenuItem(NLS.str("popup.export"))
		exportMenu.addActionListener {
			var savePath: Path? = null
			when (resource.getType()) {
				JResource.JResType.ROOT,
				JResource.JResType.DIR,
				-> savePath = getSaveDirPath(resource)

				JResource.JResType.FILE -> savePath = getSaveFilePath(resource)
			}

			if (savePath == null) {
				return@addActionListener
			}

			saveJResource(resource, savePath, true)

			LOG.info("Done saving {}", savePath)
		}
		return exportMenu
	}

	private fun getSaveFilePath(resource: JResource): Path? {
		val extension = CommonFileUtils.getFileExtension(resource.getName())

		val fileDialog = FileDialogWrapper(mainWindow, FileOpenMode.EXPORT_NODE)
		if (extension != null) {
			fileDialog.setFileExtList(listOf(extension))
		}
		val currentDir: Path? = fileDialog.getCurrentDir()
		if (currentDir != null) {
			fileDialog.setSelectedFile(currentDir.resolve(resource.getName()))
		}

		val selectedPaths = fileDialog.show()
		if (selectedPaths.size != 1) {
			return null
		}

		val selectedPath = selectedPaths[0]
		val savePath: Path
		// Append file extension if missing
		if (extension != null &&
			!selectedPath.getFileName().toString().lowercase(Locale.ROOT).endsWith(extension)
		) {
			savePath = selectedPath.resolveSibling(selectedPath.getFileName().toString() + "." + extension)
		} else {
			savePath = selectedPath
		}

		return savePath
	}

	private fun getSaveDirPath(resource: JResource): Path? {
		val fileDialog = FileDialogWrapper(mainWindow, FileOpenMode.EXPORT_NODE_FOLDER)

		val selectedPaths = fileDialog.show()
		if (selectedPaths.size != 1) {
			return null
		}

		return selectedPaths[0]
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(JResourcePopupMenu::class.java)

		private fun saveJResource(resource: JResource, savePath: Path, comingFromDialog: Boolean) {
			when (resource.getType()) {
				JResource.JResType.ROOT,
				JResource.JResType.DIR,
				-> saveJResourceDir(resource, savePath, comingFromDialog)

				JResource.JResType.FILE -> saveJResourceFile(resource, savePath, comingFromDialog)
			}
		}

		private fun saveJResourceDir(resource: JResource, savePath: Path, comingFromDialog: Boolean) {
			val subSavePath = savePath.resolve(resource.getShortName())
			try {
				if (!Files.isDirectory(subSavePath)) {
					Files.createDirectories(subSavePath)
				}
			} catch (e: IOException) {
				throw RuntimeException(e)
			}
			for (subResource in resource.getSubNodes()) {
				saveJResource(subResource, subSavePath, false)
			}
		}

		private fun saveJResourceFile(resource: JResource, savePathIn: Path, comingFromDialog: Boolean) {
			var savePath = savePathIn
			if (!comingFromDialog) {
				val fileName = Path.of(resource.getName()).getFileName()
				savePath = savePath.resolve(fileName)
			}
			when (checkNotNull(resource.getResFile()).getType()) {
				ResourceType.MANIFEST,
				ResourceType.XML,
				-> exportString(resource, savePath)

				else -> FileOpenerHelper.exportBinary(resource, savePath)
			}
		}

		private fun exportString(resource: JResource, savePath: Path) {
			SaveCode.save(resource.getCodeInfo().getCodeStr(), savePath.toFile())
		}
	}
}
