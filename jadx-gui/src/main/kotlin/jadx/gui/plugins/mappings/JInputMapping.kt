package jadx.gui.plugins.mappings

import jadx.api.ICodeInfo
import jadx.api.impl.SimpleCodeInfo
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JEditableNode
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.CodeContentPanel
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.ui.SimpleMenuItem
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.slf4j.LoggerFactory
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JPopupMenu

/**
 * 「输入映射文件」树节点。
 *
 * **做什么**：表示一个被加载到项目中的混淆映射文件（如 Proguard/Tiny 等），
 * 以可编辑文本方式展示，并支持保存回磁盘与右键移除。
 *
 * **为什么不是 `data class`**：它是树中的身份节点，需要按引用比较。
 */
class JInputMapping(private val mappingPath: Path) : JEditableNode() {

	private val mappingName: String = mappingPath.getFileName().toString()

	override fun hasContent(): Boolean = true

	override fun getContentPanel(tabbedPane: TabbedPane): ContentPanel = CodeContentPanel(tabbedPane, this)

	override fun getCodeInfo(): ICodeInfo = try {
		SimpleCodeInfo(FileUtils.readFile(mappingPath))
	} catch (e: Exception) {
		throw JadxRuntimeException("Failed to read mapping file: " + mappingPath.toAbsolutePath(), e)
	}

	override fun save(newContent: String) {
		try {
			FileUtils.writeFile(mappingPath, newContent)
			LOG.debug("Mapping saved: {}", mappingPath.toAbsolutePath())
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to write mapping file: " + mappingPath.toAbsolutePath(), e)
		}
	}

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu {
		val menu = JPopupMenu()
		menu.add(
			SimpleMenuItem(NLS.str("popup.remove")) {
				mainWindow.getRenameMappings().closeMappingsAndRemoveFromProject()
			},
		)
		return menu
	}

	override fun getSyntaxName(): String = SyntaxConstants.SYNTAX_STYLE_NONE

	override fun getJParent(): JClass? = null

	override fun getIcon(): Icon = MAPPING_ICON

	override fun getName(): String = mappingName

	override fun makeString(): String = mappingName

	override fun getTooltip(): String = mappingPath.normalize().toAbsolutePath().toString()

	companion object {
		private val LOG = LoggerFactory.getLogger(JInputMapping::class.java)

		private val MAPPING_ICON: ImageIcon = UiUtils.openSvgIcon("nodes/abbreviatePackageNames")
	}
}
