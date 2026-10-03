package jadx.gui.ui.popupmenu

import jadx.gui.JadxWrapper
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JPackage
import jadx.gui.ui.MainWindow
import jadx.gui.ui.dialog.ExcludePkgDialog
import jadx.gui.ui.dialog.RenameDialog
import jadx.gui.ui.dialog.SearchDialog
import jadx.gui.ui.filedialog.FileDialogWrapper
import jadx.gui.ui.filedialog.FileOpenMode
import jadx.gui.utils.NLS
import jadx.gui.utils.pkgs.JRenamePackage
import jadx.gui.utils.pkgs.PackageHelper
import org.slf4j.LoggerFactory
import java.awt.event.ActionEvent
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.AbstractAction
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * 包节点的右键菜单。
 *
 * **做什么**：提供「排除/取消排除包」「重命名包」「导出包」「搜索包内文本」等操作。
 * 导出包时会递归创建目录并逐个保存其中的类。
 *
 * **线程模型**：菜单动作在 EDT 上执行，导出为同步文件写入。
 */
class JPackagePopupMenu(private val mainWindow: MainWindow, pkg: JPackage) : JPopupMenu() {

	init {
		add(makeExcludeItem(pkg))
		add(makeExcludeItem())
		add(makeRenameMenuItem(pkg))
		add(makeExportSubMenu(pkg))
		add(makeSearchItem(pkg))
	}

	private fun makeRenameMenuItem(pkg: JPackage): JMenuItem {
		val renameSubMenu = JMenu(NLS.str("popup.rename"))
		val packageHelper: PackageHelper = mainWindow.getCacheObject().getPackageHelper()
		val nodes = packageHelper.getRenameNodes(pkg)
		for (node in nodes) {
			val pkgPartItem = JMenuItem(node.getTitle(), node.getIcon())
			pkgPartItem.addActionListener { rename(node) }
			renameSubMenu.add(pkgPartItem)
		}
		return renameSubMenu
	}

	private fun rename(pkg: JRenamePackage) {
		LOG.debug("Renaming package: {}", pkg)
		RenameDialog.rename(mainWindow, pkg)
	}

	private fun makeExcludeItem(pkg: JPackage): JMenuItem {
		val excludeItem = JCheckBoxMenuItem(NLS.str("popup.exclude"))
		excludeItem.setSelected(!pkg.isEnabled)
		excludeItem.addItemListener {
			val wrapper: JadxWrapper = mainWindow.getWrapper()
			val fullName = checkNotNull(pkg.getPkg()).getFullName()
			if (excludeItem.isSelected()) {
				wrapper.addExcludedPackage(fullName)
			} else {
				wrapper.removeExcludedPackage(fullName)
			}
			mainWindow.reopen()
		}
		return excludeItem
	}

	private fun makeExportSubMenu(pkg: JPackage): JMenuItem {
		val exportSubMenu = JMenu(NLS.str("popup.export"))

		exportSubMenu.add(makeExportMenuItem(pkg, NLS.str("tabs.code"), JClassExportType.Code))
		exportSubMenu.add(makeExportMenuItem(pkg, NLS.str("tabs.smali"), JClassExportType.Smali))
		exportSubMenu.add(makeExportMenuItem(pkg, "Simple", JClassExportType.Simple))
		exportSubMenu.add(makeExportMenuItem(pkg, "Fallback", JClassExportType.Fallback))

		return exportSubMenu
	}

	/** 为指定的导出类型创建一个菜单项。 */
	fun makeExportMenuItem(pkg: JPackage, label: String, exportType: JClassExportType): JMenuItem {
		val exportMenuItem = JMenuItem(label)
		exportMenuItem.addActionListener {
			val fileDialog = FileDialogWrapper(mainWindow, FileOpenMode.EXPORT_NODE_FOLDER)

			val selectedPaths = fileDialog.show()
			if (selectedPaths.size != 1) {
				return@addActionListener
			}

			val savePath = selectedPaths[0]
			saveJPackage(pkg, savePath, exportType)
		}

		return exportMenuItem
	}

	private fun makeExcludeItem(): JMenuItem = JMenuItem(object : AbstractAction(NLS.str("popup.exclude_packages")) {
		override fun actionPerformed(e: ActionEvent) {
			ExcludePkgDialog(mainWindow).isVisible = true
		}
	})

	private fun makeSearchItem(pkg: JPackage): JMenuItem {
		val searchItem = JMenuItem(NLS.str("menu.text_search"))
		searchItem.addActionListener {
			val fullName = checkNotNull(pkg.getPkg()).getFullName()
			LOG.debug("Searching package: {}", fullName)
			SearchDialog.searchPackage(mainWindow, fullName)
		}
		return searchItem
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(JPackagePopupMenu::class.java)

		private fun saveJPackage(pkg: JPackage, savePath: Path, exportType: JClassExportType) {
			val subSavePath = savePath.resolve(pkg.getName())
			try {
				if (!Files.isDirectory(subSavePath)) {
					Files.createDirectories(subSavePath)
				}
			} catch (e: IOException) {
				throw RuntimeException(e)
			}
			for (jClass in pkg.getClasses()) {
				val fileName = jClass.getName() + "." + exportType.extension
				JClassPopupMenu.saveJClass(jClass, subSavePath.resolve(fileName), exportType)
			}
			for (subPkg in pkg.getSubPackages()) {
				saveJPackage(subPkg, subSavePath, exportType)
			}
		}
	}
}
