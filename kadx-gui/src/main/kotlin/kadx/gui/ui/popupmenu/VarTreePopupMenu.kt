package kadx.gui.ui.popupmenu

import kadx.core.dex.instructions.args.ArgType
import kadx.gui.ui.MainWindow
import kadx.gui.ui.dialog.SetValueDialog
import kadx.gui.ui.panel.JDebuggerPanel.ValueTreeNode
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

/**
 * 调试器变量树的右键菜单。
 *
 * **做什么**：提供「复制值」「设置值」「改为 0」「改为 1」等操作，
 * 后三者通过 [IDebugController.modifyRegValue] 修改寄存器/字段。
 *
 * **线程模型**：菜单动作在 EDT 上执行。
 */
class VarTreePopupMenu(private val mainWindow: MainWindow) : JPopupMenu() {

	private var valNode: ValueTreeNode? = null

	init {
		addItems()
	}

	/** 记录当前操作的变量节点并弹出菜单。 */
	fun show(treeNode: ValueTreeNode, invoker: Component, x: Int, y: Int) {
		valNode = treeNode
		super.show(invoker, x, y)
	}

	private fun addItems() {
		val copyValItem = JMenuItem(object : AbstractAction(NLS.str("debugger.popup_copy_value")) {
			override fun actionPerformed(e: ActionEvent) {
				var value = checkNotNull(valNode).getValue()
				if (value != null) {
					if (value.startsWith("\"") && value.endsWith("\"")) {
						value = value.substring(1, value.length - 1)
					}
					val stringSelection = StringSelection(value)
					val clipboard = Toolkit.getDefaultToolkit().getSystemClipboard()
					clipboard.setContents(stringSelection, null)
				}
			}
		})
		val setValItem = JMenuItem(object : AbstractAction(NLS.str("debugger.popup_set_value")) {
			override fun actionPerformed(e: ActionEvent) {
				SetValueDialog(mainWindow, checkNotNull(valNode)).isVisible = true
			}
		})

		val zeroItem = JMenuItem(object : AbstractAction(NLS.str("debugger.popup_change_to_zero")) {
			override fun actionPerformed(event: ActionEvent) {
				try {
					mainWindow.getDebuggerPanel()
						.dbgController
						.modifyRegValue(checkNotNull(valNode), ArgType.INT, 0)
				} catch (e: Exception) {
					LOG.error("Change to zero failed", e)
					UiUtils.showMessageBox(mainWindow, e.message ?: "")
				}
			}
		})
		val oneItem = JMenuItem(object : AbstractAction(NLS.str("debugger.popup_change_to_one")) {
			override fun actionPerformed(event: ActionEvent) {
				try {
					mainWindow.getDebuggerPanel()
						.dbgController
						.modifyRegValue(checkNotNull(valNode), ArgType.INT, 1)
				} catch (e: Exception) {
					LOG.error("Change to one failed", e)
					UiUtils.showMessageBox(mainWindow, e.message ?: "")
				}
			}
		})

		add(copyValItem)
		add(Separator())
		add(setValItem)
		add(zeroItem)
		add(oneItem)
		add(zeroItem)
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(VarTreePopupMenu::class.java)
	}
}
