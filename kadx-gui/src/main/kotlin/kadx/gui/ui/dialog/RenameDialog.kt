package kadx.gui.ui.dialog

import kadx.api.metadata.ICodeNodeRef
import kadx.api.plugins.events.types.NodeRenamedByUser
import kadx.core.utils.Utils
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JNode
import kadx.gui.treemodel.JPackage
import kadx.gui.treemodel.JRenameNode
import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
import kadx.gui.utils.TextStandardActions
import kadx.gui.utils.pkgs.JRenamePackage
import kadx.gui.utils.ui.DocumentUpdateListener
import kadx.gui.utils.ui.NodeLabel
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTextField
import javax.swing.SwingUtilities

/**
 * 重命名对话框：修改类/方法/字段/包的名字。
 *
 * **做什么**：展示当前名字并实时校验新名字，确认后发送 [NodeRenamedByUser] 事件，
 * 由重命名服务真正执行替换。
 *
 * **为什么保留 Swing 线程模型**：对话框在 EDT 上创建与显示。
 */
class RenameDialog private constructor(
	mainWindow: MainWindow,
	renameNode: JRenameNode,
) : CommonDialog(mainWindow) {

	private val node: JRenameNode = renameNode.replace()

	private lateinit var renameField: JTextField
	private lateinit var renameBtn: JButton

	private fun initRenameField() {
		renameField.text = node.getName()
		renameField.selectAll()
	}

	private fun checkNewName(newName: String): Boolean {
		if (newName.isEmpty()) {
			// 空名字表示重置重命名（恢复原始名字）
			return true
		}
		val valid = node.isValidName(newName)
		if (renameBtn.isEnabled != valid) {
			renameBtn.isEnabled = valid
			renameField.putClientProperty("JComponent.outline", if (valid) "" else "error")
		}
		return valid
	}

	private fun rename() {
		rename(renameField.getText().trim())
	}

	private fun resetName() {
		rename("")
	}

	private fun rename(newName: String) {
		if (!checkNewName(newName)) {
			return
		}
		val oldName = node.getName()
		val newNodeName: String
		val reset = newName.isEmpty()
		if (reset) {
			node.removeAlias()
			newNodeName = Utils.getOrElse(node.getJavaNode().getName(), "")
		} else {
			newNodeName = newName
		}
		sendRenameEvent(oldName, newNodeName, reset)
		dispose()
	}

	private fun sendRenameEvent(oldName: String?, newName: String, reset: Boolean) {
		val nodeRef: ICodeNodeRef = node.getJavaNode().getCodeNodeRef()
		val event = NodeRenamedByUser(nodeRef, checkNotNull(oldName), newName)
		event.setRenameNode(node)
		event.setResetName(reset)
		mainWindow.events().send(event)
	}
	protected fun initButtonsPanel(): JPanel {
		val resetButton = JButton(NLS.str("common_dialog.reset"))
		resetButton.addActionListener { resetName() }

		val cancelButton = JButton(NLS.str("common_dialog.cancel"))
		cancelButton.addActionListener { dispose() }

		renameBtn = JButton(NLS.str("common_dialog.ok"))
		renameBtn.addActionListener { rename() }
		rootPane.defaultButton = renameBtn

		val buttonPane = JPanel()
		buttonPane.setLayout(BoxLayout(buttonPane, BoxLayout.LINE_AXIS))
		buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10))
		buttonPane.add(resetButton)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(Box.createHorizontalGlue())
		buttonPane.add(renameBtn)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(cancelButton)
		return buttonPane
	}
	private fun initUI() {
		val lbl = JLabel(NLS.str("popup.rename"))
		val nodeLabel = NodeLabel(node.getTitle())
		nodeLabel.setIcon(node.getIcon())
		if (node is JNode) {
			nodeLabel.disableHtml(node.disableHtml())
		} else if (node is JRenamePackage) {
			// TODO: 直接从 JRenameNode 获取
			nodeLabel.disableHtml(node.getTitle() != JPackage.PACKAGE_DEFAULT_HTML_STR)
		}
		lbl.setLabelFor(nodeLabel)

		renameField = JTextField(40)
		renameField.setFont(mainWindow.getSettings().codeFont)
		renameField.getDocument().addDocumentListener(DocumentUpdateListener { checkNewName(renameField.getText()) })
		renameField.addActionListener { rename() }
		TextStandardActions(renameField)

		val renamePane = JPanel()
		renamePane.setLayout(FlowLayout(FlowLayout.LEFT))
		renamePane.add(lbl)
		renamePane.add(nodeLabel)
		renamePane.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10))

		val textPane = JPanel()
		textPane.setLayout(BoxLayout(textPane, BoxLayout.PAGE_AXIS))
		textPane.add(renameField)
		if (node is JClass) {
			textPane.add(JLabel(NLS.str("rename_dialog.class_help")))
		}
		textPane.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10))

		val buttonPane = initButtonsPanel()

		val contentPane: Container = getContentPane()
		contentPane.add(renamePane, BorderLayout.PAGE_START)
		contentPane.add(textPane, BorderLayout.CENTER)
		contentPane.add(buttonPane, BorderLayout.PAGE_END)

		title = NLS.str("popup.rename")
		commonWindowInit()
	}
	init {
		initUI()
	}

	companion object {
		private const val serialVersionUID = -3269715644416902410L

		/**
		 * 异步打开重命名对话框。
		 *
		 * @return 固定返回 `true`（与原 Java 保持一致，表示已提交打开请求）
		 */
		fun rename(mainWindow: MainWindow, node: JRenameNode): Boolean {
			SwingUtilities.invokeLater {
				val renameDialog = RenameDialog(mainWindow, node)
				renameDialog.initRenameField()
				renameDialog.isVisible = true
			}
			return true
		}

		fun buildRenamePopup(mainWindow: MainWindow, node: JRenameNode): JPopupMenu {
			val menu = JPopupMenu()
			menu.add(buildRenamePopupMenuItem(mainWindow, node))
			return menu
		}

		fun buildRenamePopupMenuItem(mainWindow: MainWindow, node: JRenameNode): JMenuItem {
			val jmi = JMenuItem(NLS.str("popup.rename"))
			jmi.addActionListener { rename(mainWindow, node) }
			jmi.isEnabled = node.canRename()

			return jmi
		}
	}
}
