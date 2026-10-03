package jadx.gui.ui.graphs

import jadx.core.utils.ListUtils
import jadx.gui.ui.MainWindow
import jadx.gui.ui.filedialog.FileDialogWrapper
import jadx.gui.ui.filedialog.FileOpenMode
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.layout.WrapLayout
import jadx.gui.utils.ui.MouseListenerAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseEvent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import java.util.Collections
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenuBar
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.WindowConstants

/**
 * 图形窗口基类（`JFrame`）：承载 [GraphPanel] 与顶部菜单栏。
 *
 * **做什么**：统一处理窗口位置保存/恢复、ESC 关闭、菜单整体启用/禁用，
 * 以及一个「保存为 SVG」按钮；子类只需实现 [addMenuBar] 并调用 [GraphPanel.setGraph]。
 *
 * **线程模型**：保持原 Swing 模型，不引入协程。
 */
abstract class GraphDialog(
	private val mainWindow: MainWindow,
	title: String,
) : JFrame(title) {

	private val panel: GraphPanel
	private var menuBar: JMenuBar? = null

	protected constructor(mainWindow: MainWindow) : this(mainWindow, NLS.str("graph_viewer.default_title"))

	init {
		minimumSize = MIN_WINDOW_SIZE
		defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
		UiUtils.addEscapeShortCutToDispose(this)
		setLocationRelativeTo(null)
		loadWindowPos()

		panel = GraphPanel(this)
		panel.isFocusable = true
		panel.addMouseListener(object : MouseListenerAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				requestFocusInWindow()
			}
		})
		setLayout(BorderLayout())
		add(panel, BorderLayout.CENTER)
	}

	/** 创建顶部菜单栏并挂上「保存 SVG」按钮。子类可覆写并追加自己的控件。 */
	open fun addMenuBar(): JMenuBar {
		val menuBar = JMenuBar()
		menuBar.setLayout(WrapLayout(FlowLayout.LEFT))
		add(menuBar, BorderLayout.PAGE_START)
		this.menuBar = menuBar

		val saveButton = JButton(NLS.str("graph_viewer.save_graph"))
		saveButton.isEnabled = false
		saveButton.addActionListener {
			try {
				val fileDialog = FileDialogWrapper(mainWindow, FileOpenMode.CUSTOM_SAVE)
				fileDialog.setTitle(NLS.str("graph_viewer.save_graph"))
				fileDialog.setFileExtList(Collections.singletonList("svg"))
				fileDialog.setSelectionMode(JFileChooser.FILES_ONLY)
				val savePaths: List<Path> = fileDialog.show()
				if (!savePaths.isEmpty()) {
					val saveFile: File = ListUtils.first(savePaths).toFile()
					getPanel().exportSVG(saveFile)
				}
			} catch (ex: Exception) {
				LOG.error("Failed to save file: ", ex)
				JOptionPane.showMessageDialog(
					this,
					NLS.str("graph_viewer.file_failure"),
					NLS.str("graph_viewer.file_failure"),
					JOptionPane.INFORMATION_MESSAGE,
				)
			}
		}
		val menuBarPanel = JPanel()
		menuBarPanel.isOpaque = false
		menuBarPanel.add(saveButton)
		menuBar.add(menuBarPanel)
		return menuBar
	}

	internal fun enableMenu() {
		setAllEnabled(true, checkNotNull(menuBar))
	}

	internal open fun disableMenu() {
		setAllEnabled(false, checkNotNull(menuBar))
	}

	private fun setAllEnabled(isEnabled: Boolean, component: JComponent) {
		component.isEnabled = isEnabled
		val components: Array<Component> = component.components
		for (subComponent in components) {
			if (subComponent is JComponent) {
				setAllEnabled(isEnabled, subComponent)
			} else {
				subComponent.isEnabled = isEnabled
			}
		}
	}

	fun loadWindowPos() {
		if (!mainWindow.getSettings().loadWindowPos(this)) {
			preferredSize = MIN_WINDOW_SIZE
		}
	}

	override fun dispose() {
		try {
			mainWindow.getSettings().saveWindowPos(this)
		} catch (e: Exception) {
			LOG.warn("Failed to save window size and position", e)
		}
		super.dispose()
	}

	protected fun getPanel(): GraphPanel = panel

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(GraphDialog::class.java)

		private val MIN_WINDOW_SIZE = Dimension(800, 500)

		/** 构造一个只读的错误文本区（用于把错误展示在图形区域内）。 */
		@JvmStatic
		fun graphError(errorMessage: String): JTextArea {
			val errorText = JTextArea()
			errorText.setText(errorMessage)
			errorText.isVisible = true
			errorText.isEditable = false
			errorText.setLineWrap(false)
			return errorText
		}

		/** 把异常堆栈写入只读文本区，附带默认错误提示。 */
		@JvmStatic
		fun graphError(error: Exception): JTextArea {
			val errorText = JTextArea()
			val stringWriter = StringWriter()
			val printWriter = PrintWriter(stringWriter)
			stringWriter.write(NLS.str("graph_viewer.default_error"))
			stringWriter.write(": ")
			error.printStackTrace(printWriter)
			errorText.setText(stringWriter.toString())
			errorText.isVisible = true
			errorText.isEditable = false
			errorText.setLineWrap(false)
			return errorText
		}
	}
}
