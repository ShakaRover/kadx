package jadx.gui.treemodel

import jadx.api.ICodeInfo
import jadx.api.impl.SimpleCodeInfo
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.AbstractCodeArea
import jadx.gui.ui.codearea.CodeContentPanel
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.Icons
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JPopupMenu

/**
 * 可编辑的 smali 输入文件节点。
 *
 * **做什么**：把 `.smali` 输入文件当作可编辑文本打开，保存时直接写回磁盘。
 *
 * **为什么不是 `data class`**：相等性由文件路径决定，且需要按身份参与树比较。
 */
class JInputSmaliFile(private val filePath: Path) : JEditableNode() {

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu = JInputFile.buildInputFilePopupMenu(mainWindow, filePath)

	override fun hasContent(): Boolean = true

	override fun getContentPanel(tabbedPane: TabbedPane): ContentPanel = CodeContentPanel(tabbedPane, this)

	override fun getSyntaxName(): String = AbstractCodeArea.SYNTAX_STYLE_SMALI

	override fun getCodeInfo(): ICodeInfo {
		try {
			return SimpleCodeInfo(FileUtils.readFile(filePath))
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to read file: " + filePath.toAbsolutePath(), e)
		}
	}

	override fun save(newContent: String) {
		try {
			FileUtils.writeFile(filePath, newContent)
			LOG.debug("File saved: {}", filePath.toAbsolutePath())
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to write file: " + filePath.toAbsolutePath(), e)
		}
	}

	override fun getJParent(): JClass? = null

	override fun getIcon(): Icon = Icons.FILE

	override fun makeString(): String = filePath.fileName.toString()

	override fun getTooltip(): String = filePath.normalize().toAbsolutePath().toString()

	override fun hashCode(): Int = filePath.hashCode()

	override fun equals(other: Any?): Boolean {
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		return (other as JInputSmaliFile).filePath == filePath
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JInputSmaliFile::class.java)
	}
}
