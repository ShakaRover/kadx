package jadx.gui.ui.dialog

import jadx.api.JavaMethod
import jadx.gui.ui.MainWindow
import jadx.gui.ui.cellrenders.MethodsListRenderer
import jadx.gui.utils.NLS
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.DefaultListSelectionModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.WindowConstants

/**
 * 方法选择对话框：列出类的全部方法，供调用方选择若干项（如 Frida 代码生成）。
 *
 * **做什么**：构造时立即展示（模态），确认后通过 [listConsumer] 回调选中的方法列表。
 *
 * **为什么保留 Swing 线程模型**：所有操作都在 EDT 上，不引入协程。
 */
class MethodsDialog(
	mainWindow: MainWindow,
	methods: List<JavaMethod>,
	private val listConsumer: (List<JavaMethod>) -> Unit,
) : CommonDialog(mainWindow) {

	private lateinit var methodList: JList<JavaMethod>

	init {
		initUI(methods)
		isVisible = true
	}

	private fun initUI(methods: List<JavaMethod>) {
		title = NLS.str("methods_dialog.title")

		val defaultListModel = DefaultListModel<JavaMethod>()
		defaultListModel.addAll(methods)

		methodList = JList()
		methodList.setModel(defaultListModel)
		methodList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
		methodList.setCellRenderer(MethodsListRenderer())
		// 自定义选择模型：再次点击已选项即取消选择（切换语义）
		methodList.setSelectionModel(object : DefaultListSelectionModel() {
			override fun setSelectionInterval(index0: Int, index1: Int) {
				if (super.isSelectedIndex(index0)) {
					super.removeSelectionInterval(index0, index1)
				} else {
					super.addSelectionInterval(index0, index1)
				}
			}
		})

		val scrollPane = JScrollPane(methodList)
		val buttonPane = initButtonsPanel()

		val contentPanel = JPanel()
		contentPanel.setLayout(BorderLayout(5, 5))
		contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))
		contentPanel.add(scrollPane, BorderLayout.CENTER)
		contentPanel.add(buttonPane, BorderLayout.PAGE_END)
		getContentPane().add(contentPanel)

		pack()
		setSize(500, 300)
		setLocationRelativeTo(null)
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
	}

	protected fun initButtonsPanel(): JPanel {
		val cancelButton = JButton(NLS.str("common_dialog.cancel"))
		cancelButton.addActionListener { dispose() }

		val okBtn = JButton(NLS.str("common_dialog.ok"))
		okBtn.addActionListener { generateForSelected() }
		rootPane.defaultButton = okBtn

		val buttonPane = JPanel()
		buttonPane.setLayout(BoxLayout(buttonPane, BoxLayout.LINE_AXIS))
		buttonPane.add(Box.createHorizontalGlue())
		buttonPane.add(okBtn)
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(cancelButton)
		return buttonPane
	}

	private fun generateForSelected() {
		val selectedMethods = methodList.getSelectedValuesList()
		if (selectedMethods.isNotEmpty()) {
			this.listConsumer(selectedMethods)
		}
		dispose()
	}

	companion object {
		private const val serialVersionUID = 1L
	}
}
