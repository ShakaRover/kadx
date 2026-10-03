package jadx.gui.utils

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JPopupMenu
import javax.swing.KeyStroke
import javax.swing.text.JTextComponent
import javax.swing.undo.UndoManager

/**
 * 给任意 [JTextComponent] 挂载标准的「撤销/重做/剪切/复制/粘贴/删除/全选」菜单与快捷键。
 *
 * **做什么**：[attach] 一次即可让文本框拥有右键菜单和 Ctrl+Z / Ctrl+R 快捷键。
 *
 * **Swing 说明**：完全保留原 Java 的监听器结构（匿名 [MouseAdapter] + 各 [AbstractAction]），
 * 未改动线程模型。
 */
class TextStandardActions(private val textComponent: JTextComponent) {

	private val popup = JPopupMenu()
	private val undoManager: UndoManager = UndoManager()

	private lateinit var undoAction: Action
	private lateinit var redoAction: Action
	private lateinit var cutAction: Action
	private lateinit var copyAction: Action
	private lateinit var pasteAction: Action
	private lateinit var deleteAction: Action
	private lateinit var selectAllAction: Action

	init {
		initActions()
		addPopupItems()
		addKeyActions()

		registerListeners()
	}

	private fun initActions() {
		undoAction = object : AbstractAction(NLS.str("popup.undo")) {
			override fun actionPerformed(ae: ActionEvent) {
				if (undoManager.canUndo()) {
					undoManager.undo()
				}
			}
		}
		redoAction = object : AbstractAction(NLS.str("popup.redo")) {
			override fun actionPerformed(ae: ActionEvent) {
				if (undoManager.canRedo()) {
					undoManager.redo()
				}
			}
		}
		cutAction = object : AbstractAction(NLS.str("popup.cut")) {
			override fun actionPerformed(ae: ActionEvent) {
				textComponent.cut()
			}
		}
		copyAction = object : AbstractAction(NLS.str("popup.copy")) {
			override fun actionPerformed(ae: ActionEvent) {
				textComponent.copy()
			}
		}
		pasteAction = object : AbstractAction(NLS.str("popup.paste")) {
			override fun actionPerformed(ae: ActionEvent) {
				textComponent.paste()
			}
		}
		deleteAction = object : AbstractAction(NLS.str("popup.delete")) {
			override fun actionPerformed(ae: ActionEvent) {
				textComponent.replaceSelection("")
			}
		}
		selectAllAction = object : AbstractAction(NLS.str("popup.select_all")) {
			override fun actionPerformed(ae: ActionEvent) {
				textComponent.selectAll()
			}
		}
	}

	private fun addPopupItems() {
		popup.add(undoAction)
		popup.add(redoAction)
		popup.addSeparator()
		popup.add(cutAction)
		popup.add(copyAction)
		popup.add(pasteAction)
		popup.add(deleteAction)
		popup.addSeparator()
		popup.add(selectAllAction)
	}

	private fun addKeyActions() {
		val undoKey = KeyStroke.getKeyStroke(KeyEvent.VK_Z, UiUtils.ctrlButton())
		textComponent.getInputMap().put(undoKey, undoAction)
		val redoKey = KeyStroke.getKeyStroke(KeyEvent.VK_R, UiUtils.ctrlButton())
		textComponent.getInputMap().put(redoKey, redoAction)
	}

	private fun registerListeners() {
		textComponent.addMouseListener(object : MouseAdapter() {
			override fun mouseReleased(e: MouseEvent) {
				if (e.getButton() == 3 && e.getSource() === textComponent) {
					process(e)
				}
			}
		})
		textComponent.getDocument().addUndoableEditListener { event -> undoManager.addEdit(event.getEdit()) }
	}

	private fun process(e: MouseEvent) {
		textComponent.requestFocus()

		val enabled = textComponent.isEnabled
		val editable = textComponent.isEditable
		val nonempty = !(textComponent.getText() == null || textComponent.getText().isEmpty())
		val marked = textComponent.getSelectedText() != null
		val pasteAvailable = Toolkit.getDefaultToolkit().getSystemClipboard()
			.getContents(null).isDataFlavorSupported(DataFlavor.stringFlavor)

		undoAction.setEnabled(enabled && editable && undoManager.canUndo())
		redoAction.setEnabled(enabled && editable && undoManager.canRedo())
		cutAction.setEnabled(enabled && editable && marked)
		copyAction.setEnabled(enabled && marked)
		pasteAction.setEnabled(enabled && editable && pasteAvailable)
		deleteAction.setEnabled(enabled && editable && marked)
		selectAllAction.setEnabled(enabled && nonempty)

		var nx = e.getX()
		if (nx > 500) {
			nx = nx - popup.size.width
		}
		popup.show(e.getComponent(), nx, e.getY() - popup.size.height)
	}

	companion object {
		/** 给 [textComponent] 挂载标准文本操作（构造即完成注册）。 */
		fun attach(textComponent: JTextComponent) {
			TextStandardActions(textComponent)
		}
	}
}
