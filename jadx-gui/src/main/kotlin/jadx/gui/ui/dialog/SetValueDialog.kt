package jadx.gui.ui.dialog

import jadx.core.dex.instructions.args.ArgType
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.ui.MainWindow
import jadx.gui.ui.panel.JDebuggerPanel.ValueTreeNode
import jadx.gui.utils.NLS
import jadx.gui.utils.TextStandardActions
import jadx.gui.utils.UiUtils
import java.awt.BorderLayout
import java.awt.Dialog.ModalityType
import java.awt.Label
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.util.AbstractMap.SimpleEntry
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.WindowConstants

/**
 * 调试器「修改寄存器/字段值」对话框。
 *
 * **做什么**：让用户选择值类型（int/String/long/float/double/Object id）并输入新值，
 * 通过 [jadx.gui.ui.panel.IDebugController.modifyRegValue] 应用。
 *
 * **为什么保留 Swing 线程模型**：全部逻辑在 EDT 上执行，不引入协程。
 */
class SetValueDialog(
	private val mainWindow: MainWindow,
	private val valNode: ValueTreeNode,
) : JDialog(mainWindow) {

	init {
		initUI()
		UiUtils.addEscapeShortCutToDispose(this)
		title = valNode.toString()
	}

	private fun initUI() {
		val valField = JTextField()
		TextStandardActions.attach(valField)
		val valPane = JPanel(BorderLayout(5, 5))
		valPane.add(JLabel(NLS.str("set_value_dialog.label_value")), BorderLayout.WEST)
		valPane.add(valField, BorderLayout.CENTER)

		val btnPane = JPanel()
		btnPane.setLayout(BoxLayout(btnPane, BoxLayout.LINE_AXIS))
		val setValueBtn = JButton(NLS.str("set_value_dialog.btn_set"))
		btnPane.add(Label())
		btnPane.add(setValueBtn)

		// 回车键等价于点击「设置」按钮
		UiUtils.addKeyBinding(
			valField,
			KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
			"set value",
			object : AbstractAction() {
				override fun actionPerformed(e: ActionEvent) {
					setValueBtn.doClick()
				}
			},
		)

		val typePane = JPanel()
		typePane.setLayout(BoxLayout(typePane, BoxLayout.LINE_AXIS))
		val rbs = ArrayList<JRadioButton>(6)
		rbs.add(JRadioButton("int"))
		rbs.add(JRadioButton("String"))
		rbs.add(JRadioButton("long"))
		rbs.add(JRadioButton("float"))
		rbs.add(JRadioButton("double"))
		rbs.add(JRadioButton("Object id"))
		rbs[0].isSelected = true // 默认选中 int

		val rbGroup = ButtonGroup()
		rbs.forEach { rbGroup.add(it) }
		rbs.forEach { typePane.add(it) }

		val mainPane = JPanel(BorderLayout(5, 5))
		mainPane.add(typePane, BorderLayout.NORTH)
		mainPane.add(valPane, BorderLayout.CENTER)
		mainPane.add(btnPane, BorderLayout.SOUTH)
		mainPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		getContentPane().add(mainPane)

		this.title = NLS.str("set_value_dialog.title")

		pack()
		setSize(480, 160)
		setLocationRelativeTo(null)
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
		setModalityType(ModalityType.MODELESS)
		UiUtils.addEscapeShortCutToDispose(this)

		setValueBtn.addActionListener(object : AbstractAction() {
			override fun actionPerformed(e: ActionEvent) {
				val ok: Boolean
				try {
					val type = getType()
					if (type != null) {
						ok = mainWindow
							.getDebuggerPanel()
							.dbgController
							.modifyRegValue(valNode, type.key, type.value)
					} else {
						UiUtils.showMessageBox(mainWindow, NLS.str("set_value_dialog.sel_type"))
						return
					}
				} catch (except: JadxRuntimeException) {
					UiUtils.showMessageBox(mainWindow, except.message ?: "")
					return
				}
				if (ok) {
					dispose()
				} else {
					UiUtils.showMessageBox(mainWindow, NLS.str("set_value_dialog.neg_msg"))
				}
			}

			private fun getType(): Map.Entry<ArgType, Any>? {
				val value = valField.getText()
				for (rb in rbs) {
					if (rb.isSelected) {
						return when (rb.getText()) {
							"int" -> SimpleEntry(ArgType.INT, value.toInt())
							"String" -> SimpleEntry(ArgType.STRING, value)
							"long" -> SimpleEntry(ArgType.LONG, value.toLong())
							"float" -> SimpleEntry(ArgType.FLOAT, value.toFloat())
							"double" -> SimpleEntry(ArgType.DOUBLE, value.toDouble())
							"Object id" -> SimpleEntry(ArgType.OBJECT, value.toLong())
							else -> throw JadxRuntimeException("Unexpected type: " + rb.getText())
						}
					}
				}
				return null
			}
		})
	}

	companion object {
		private const val serialVersionUID = -1111111202103121002L
	}
}
