package jadx.gui.ui.dialog

import jadx.api.data.CommentStyle
import jadx.api.data.ICodeComment
import jadx.api.data.impl.JadxCodeComment
import jadx.api.data.impl.JadxCodeData
import jadx.gui.settings.JadxProject
import jadx.gui.ui.codearea.CodeArea
import jadx.gui.utils.NLS
import jadx.gui.utils.TextStandardActions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.Collections
import java.util.function.Consumer
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingConstants

/**
 * 添加 / 编辑代码注释的对话框。
 *
 * **做什么**：输入注释文本并选择注释风格，写入工程的 [JadxCodeData]。
 *
 * **为什么保留 Swing 线程模型**：对话框逻辑全部在 EDT 上执行，不引入协程。
 */
class CommentDialog(
	private val codeArea: CodeArea,
	private val comment: ICodeComment,
	private val updateComment: Boolean,
) : CommonDialog(codeArea.getMainWindow()) {

	private lateinit var commentArea: JTextArea
	private lateinit var styleCombo: JComboBox<CommentStyle>

	private fun apply() {
		val newCommentStr = commentArea.getText().trim()
		if (newCommentStr.isEmpty()) {
			if (updateComment) {
				remove()
			} else {
				cancel()
			}
			return
		}
		val style = styleCombo.getSelectedItem() as CommentStyle
		val newComment: ICodeComment = JadxCodeComment(comment.getNodeRef(), comment.getCodeRef(), newCommentStr, style)
		if (updateComment) {
			updateCommentsData(codeArea) { list ->
				list.remove(comment)
				list.add(newComment)
			}
		} else {
			updateCommentsData(codeArea) { list -> list.add(newComment) }
		}
		dispose()
	}

	private fun remove() {
		updateCommentsData(codeArea) { list -> list.removeIf { c -> c === comment } }
		dispose()
	}

	private fun cancel() {
		dispose()
	}
	private fun initUI() {
		commentArea = JTextArea()
		TextStandardActions.attach(commentArea)
		commentArea.setEditable(true)
		commentArea.setFont(mainWindow.getSettings().getCodeFont())
		commentArea.setAlignmentX(Component.LEFT_ALIGNMENT)

		commentArea.addKeyListener(object : KeyAdapter() {
			override fun keyPressed(e: KeyEvent) {
				when (e.getKeyCode()) {
					KeyEvent.VK_ENTER ->
						if (e.isShiftDown || e.isControlDown) {
							commentArea.insert("\n", commentArea.getCaretPosition())
						} else {
							apply()
						}

					KeyEvent.VK_ESCAPE -> cancel()
				}
			}
		})
		if (updateComment) {
			commentArea.setText(comment.getComment())
		}

		val textAreaScrollPane = JScrollPane(commentArea)
		textAreaScrollPane.setAlignmentX(LEFT_ALIGNMENT)

		styleCombo = JComboBox(CommentStyle.values())
		styleCombo.setSelectedItem(comment.getStyle())

		val commentLabel = JLabel(NLS.str("comment_dialog.label"), SwingConstants.LEFT)
		val styleLabel = JLabel(NLS.str("comment_dialog.style"), SwingConstants.LEFT)
		val usageLabel = JLabel(NLS.str("comment_dialog.usage"), SwingConstants.LEFT)

		val inputPanel = JPanel()
		inputPanel.setLayout(BoxLayout(inputPanel, BoxLayout.PAGE_AXIS))
		inputPanel.add(commentLabel)
		inputPanel.add(Box.createRigidArea(Dimension(0, 5)))
		inputPanel.add(textAreaScrollPane)
		inputPanel.add(Box.createRigidArea(Dimension(0, 5)))
		inputPanel.add(usageLabel)

		val stylePanel = JPanel()
		stylePanel.setLayout(BoxLayout(stylePanel, BoxLayout.LINE_AXIS))
		stylePanel.setAlignmentX(LEFT_ALIGNMENT)
		stylePanel.add(styleLabel)
		stylePanel.add(Box.createRigidArea(Dimension(5, 0)))
		stylePanel.add(styleCombo)

		val mainPanel = JPanel(BorderLayout(10, 10))
		mainPanel.add(inputPanel, BorderLayout.CENTER)
		mainPanel.add(stylePanel, BorderLayout.PAGE_END)
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))

		val buttonPane = initButtonsPanel()

		val contentPane: Container = getContentPane()
		contentPane.add(mainPanel, BorderLayout.CENTER)
		contentPane.add(buttonPane, BorderLayout.PAGE_END)

		if (updateComment) {
			title = NLS.str("comment_dialog.title.update")
		} else {
			title = NLS.str("comment_dialog.title.add")
		}
		commonWindowInit()
	}
	protected fun initButtonsPanel(): JPanel {
		val cancelButton = JButton(NLS.str("common_dialog.cancel"))
		cancelButton.addActionListener { cancel() }

		val applyStr = if (updateComment) NLS.str("common_dialog.update") else NLS.str("common_dialog.add")
		val renameBtn = JButton(applyStr)
		renameBtn.addActionListener { apply() }
		rootPane.defaultButton = renameBtn

		val removeBtn: JButton?
		if (updateComment) {
			removeBtn = JButton(NLS.str("common_dialog.remove"))
			removeBtn.addActionListener { remove() }
		} else {
			removeBtn = null
		}

		val buttonPane = JPanel()
		buttonPane.setLayout(BoxLayout(buttonPane, BoxLayout.LINE_AXIS))
		buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10))
		buttonPane.add(Box.createRigidArea(Dimension(5, 0)))
		buttonPane.add(Box.createHorizontalGlue())
		buttonPane.add(renameBtn)
		if (removeBtn != null) {
			buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
			buttonPane.add(removeBtn)
		}
		buttonPane.add(Box.createRigidArea(Dimension(10, 0)))
		buttonPane.add(cancelButton)
		return buttonPane
	}
	init {
		initUI()
	}

	companion object {
		private const val serialVersionUID = -1865682124935757528L

		private val LOG: Logger = LoggerFactory.getLogger(CommentDialog::class.java)

		/** 打开注释对话框。 */
		@JvmStatic
		fun show(codeArea: CodeArea, comment: ICodeComment, updateComment: Boolean) {
			val dialog = CommentDialog(codeArea, comment, updateComment)
			dialog.isVisible = true
		}

		/**
		 * 统一修改注释数据的入口：拷贝一份注释列表交给 [updater] 修改，
		 * 排序后写回工程，并触发代码数据重载与后台刷新。
		 */
		@JvmStatic
		fun updateCommentsData(codeArea: CodeArea, updater: Consumer<MutableList<ICodeComment>>) {
			try {
				val project: JadxProject = codeArea.getProject()
				var codeData = project.getCodeData()
				if (codeData == null) {
					codeData = JadxCodeData()
				}
				val list = ArrayList<ICodeComment>(codeData.getComments())
				updater.accept(list)
				Collections.sort(list)
				codeData.setComments(list)
				project.setCodeData(codeData)
				codeArea.getMainWindow().getWrapper().reloadCodeData()
			} catch (e: Exception) {
				LOG.error("Comment action failed", e)
			}
			try {
				// 在后台线程刷新代码，避免阻塞 UI
				codeArea.backgroundRefreshClass()
			} catch (e: Exception) {
				LOG.error("Failed to reload code", e)
			}
		}
	}
}
