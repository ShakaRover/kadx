package jadx.gui.logs

import ch.qos.logback.classic.Level
import jadx.gui.settings.JadxSettings
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.AbstractCodeArea
import jadx.gui.ui.tab.TabBlueprint
import jadx.gui.utils.NLS
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.ChangeListener

/**
 * 日志查看面板（可停靠在主窗口，也可放进独立窗口）。
 *
 * **做什么**：顶部提供模式/级别下拉框与「清空 / 停靠 / 隐藏」按钮，
 * 中部是显示日志的代码区。切换选项会重新注册 [LogAppender] 并按当前 [LogOptions] 过滤。
 *
 * **线程模型**：保持原 Swing 模型，不引入协程；仅通过 `ChangeListener` 监听标签切换。
 */
class LogPanel(
	private val mainWindow: MainWindow,
	logOptions: LogOptions,
	private val dockAction: Runnable,
	private val hideAction: Runnable,
) : JPanel() {

	private lateinit var textPane: RSyntaxTextArea
	private lateinit var modeCb: JComboBox<LogMode>
	private lateinit var levelCb: JComboBox<Level>

	/** 仅在 CURRENT_SCRIPT 模式下监听标签切换。 */
	private var activeTabListener: ChangeListener? = null

	init {
		initUI(logOptions)
		applyLogOptions(logOptions)
	}

	/** 应用新的日志选项：必要时解析当前脚本名，并重建日志监听器。 */
	fun applyLogOptions(logOptions: LogOptions) {
		var opts = logOptions
		if (opts.mode == LogMode.CURRENT_SCRIPT) {
			val scriptName = currentScriptName
			if (scriptName != null) {
				opts = LogOptions.forScript(scriptName)
			}
			registerActiveTabListener()
		} else {
			removeActiveTabListener()
		}
		// 原 Java 用 != 比较枚举引用，这里显式写成 !==
		if (modeCb.selectedItem !== opts.mode) {
			modeCb.setSelectedItem(opts.mode)
		}
		if (levelCb.selectedItem !== opts.logLevel) {
			levelCb.setSelectedItem(opts.logLevel)
		}
		registerLogListener(opts)
	}

	/** 重新加载代码区通用设置（字体/主题）。 */
	fun loadSettings() {
		AbstractCodeArea.loadCommonSettings(mainWindow, textPane)
	}

	private fun initUI(logOptions: LogOptions) {
		val settings: JadxSettings = mainWindow.getSettings()
		textPane = AbstractCodeArea.getDefaultArea(mainWindow)
		textPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15))

		modeCb = JComboBox(LogMode.values())
		modeCb.setSelectedItem(logOptions.mode)
		modeCb.addActionListener { applyLogOptions(LogOptions.forMode(modeCb.selectedItem as LogMode)) }
		val modeLabel = JLabel(NLS.str("log_viewer.mode"))
		modeLabel.setLabelFor(modeCb)

		levelCb = JComboBox(LEVEL_ITEMS)
		levelCb.setSelectedItem(logOptions.logLevel)
		levelCb.addActionListener { applyLogOptions(LogOptions.forLevel(levelCb.selectedItem as Level)) }
		val levelLabel = JLabel(NLS.str("log_viewer.log_level"))
		levelLabel.setLabelFor(levelCb)

		val clearBtn = JButton(NLS.str("log_viewer.clear"))
		clearBtn.addActionListener {
			LogCollector.instance.reset()
			textPane.setText("")
		}

		val dockBtn = JButton(if (settings.isDockLogViewer) NLS.str("log_viewer.undock") else NLS.str("log_viewer.dock"))
		dockBtn.addActionListener { dockAction.run() }

		val hideBtn = JButton(NLS.str("log_viewer.hide"))
		hideBtn.addActionListener { hideAction.run() }

		val start = JPanel()
		start.setLayout(BoxLayout(start, BoxLayout.LINE_AXIS))
		start.add(modeLabel)
		start.add(Box.createRigidArea(Dimension(5, 0)))
		start.add(modeCb)
		start.add(Box.createRigidArea(Dimension(15, 0)))
		start.add(levelLabel)
		start.add(Box.createRigidArea(Dimension(5, 0)))
		start.add(levelCb)
		start.add(Box.createRigidArea(Dimension(5, 0)))

		val end = JPanel()
		end.setLayout(BoxLayout(end, BoxLayout.LINE_AXIS))
		end.add(clearBtn)
		end.add(Box.createRigidArea(Dimension(15, 0)))
		end.add(dockBtn)
		end.add(Box.createRigidArea(Dimension(15, 0)))
		end.add(hideBtn)

		val controlPane = JPanel()
		controlPane.setLayout(BorderLayout())
		controlPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5))
		controlPane.add(start, BorderLayout.LINE_START)
		controlPane.add(end, BorderLayout.LINE_END)

		val scrollPane = JScrollPane(textPane)

		setLayout(BorderLayout(5, 5))
		add(controlPane, BorderLayout.PAGE_START)
		add(scrollPane, BorderLayout.CENTER)
	}

	private fun registerLogListener(logOptions: LogOptions) {
		val logCollector = LogCollector.instance
		logCollector.removeListenerByClass(LogAppender::class.java)
		textPane.setText("")
		logCollector.registerListener(LogAppender(logOptions, textPane))
	}

	/** 若当前标签是脚本节点，返回其名称；否则返回 null。 */
	private val currentScriptName: String? get() {
		val selectedTab: TabBlueprint? = mainWindow.getTabsController().getSelectedTab()
		if (selectedTab != null) {
			val node = selectedTab.node
			// TODO: 用自定义日志过滤器替代按类名判断
			if (node.javaClass.simpleName == "JInputScript") {
				return node.getName()
			}
		}
		return null
	}

	@Synchronized
	private fun registerActiveTabListener() {
		removeActiveTabListener()
		val listener = ChangeListener {
			val scriptName = currentScriptName
			if (scriptName != null) {
				applyLogOptions(LogOptions.forScript(scriptName))
			}
		}
		activeTabListener = listener
		mainWindow.getTabbedPane().addChangeListener(listener)
	}

	@Synchronized
	private fun removeActiveTabListener() {
		val listener = activeTabListener
		if (listener != null) {
			mainWindow.getTabbedPane().removeChangeListener(listener)
			activeTabListener = null
		}
	}

	/** 面板销毁：注销日志监听器与标签监听器。 */
	fun dispose() {
		LogCollector.instance.removeListenerByClass(LogAppender::class.java)
		removeActiveTabListener()
	}

	companion object {
		private const val serialVersionUID = -8077649118322056081L

		private val LEVEL_ITEMS = arrayOf(Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.OFF)
	}
}
