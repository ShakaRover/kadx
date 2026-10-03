package jadx.gui.ui.panel

import jadx.gui.device.debugger.LogcatController
import jadx.gui.device.protocol.ADB
import jadx.gui.device.protocol.ADBDevice
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import jadx.gui.utils.ui.NodeLabel
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BoundedRangeModel
import javax.swing.Box
import javax.swing.ImageIcon
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.JToolBar
import javax.swing.ListCellRenderer
import javax.swing.text.AttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext

/**
 * Logcat 日志面板（内嵌在调试器面板中）。
 *
 * **做什么**：显示设备日志，并支持按进程 / 日志级别过滤；顶部工具栏提供暂停/继续与清空按钮。
 * 过滤通过 [LogcatController] 的过滤器实现，日志内容按级别着色。
 *
 * **线程模型**：日志追加统一通过 [UiUtils.uiRun] 投递到 EDT，保持原 Swing 模型。
 */
class LogcatPanel(private val debugPanel: JDebuggerPanel) : JPanel() {

	private val sc: StyleContext = StyleContext.getDefaultStyleContext()
	private val defaultAset: AttributeSet = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.decode("#6c71c4"))
	private val verboseAset: AttributeSet = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.decode("#2aa198"))
	private val debugAset: AttributeSet = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.decode("#859900"))
	private val infoAset: AttributeSet = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.decode("#586e75"))
	private val warningAset: AttributeSet = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.decode("#b58900"))
	private val errorAset: AttributeSet = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.decode("#dc322f"))
	private val fatalAset: AttributeSet = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.decode("#d33682"))
	private val silentAset: AttributeSet = sc.addAttribute(SimpleAttributeSet.EMPTY, StyleConstants.Foreground, Color.decode("#002b36"))

	private val asetMap: Map<Byte, AttributeSet> = mapOf(
		1.toByte() to defaultAset,
		2.toByte() to verboseAset,
		3.toByte() to debugAset,
		4.toByte() to infoAset,
		5.toByte() to warningAset,
		6.toByte() to errorAset,
		7.toByte() to fatalAset,
		8.toByte() to silentAset,
	)

	private lateinit var logcatPane: JTextPane
	private lateinit var logcatController: LogcatController
	private var ready = false
	private var procs: List<ADB.Process>? = null
	private var pids: MutableList<Int> = ArrayList()
	private lateinit var logcatScroll: JScrollPane
	private var pid = 0

	private val pauseButton: AbstractAction = object : AbstractAction(NLS.str("logcat.pause"), ICON_PAUSE) {
		override fun actionPerformed(e: ActionEvent) {
			toggleLogcat()
		}
	}

	private val clearButton: AbstractAction = object : AbstractAction(NLS.str("logcat.clear"), CLEAR_LOGCAT) {
		override fun actionPerformed(e: ActionEvent) {
			clearLogcat()
		}
	}

	/** 构建 Logcat 界面（进程/级别过滤下拉框与工具栏）。 */
	fun showLogcat(): Boolean {
		this.removeAll()

		val pkgs = ArrayList<String>()
		pids = ArrayList()
		val procs = checkNotNull(this.procs)
		for (proc in procs.subList(1, procs.size)) { // skipping first element because it contains the column label
			pkgs.add(String.format("[pid: %-6s] %s", proc.pid, proc.name))
			pids.add(proc.pid.toInt())
		}

		val msgTypes = arrayOf(
			NLS.str("logcat.default"),
			NLS.str("logcat.verbose"),
			NLS.str("logcat.debug"),
			NLS.str("logcat.info"),
			NLS.str("logcat.warn"),
			NLS.str("logcat.error"),
			NLS.str("logcat.fatal"),
			NLS.str("logcat.silent"),
		)
		val msgIndex = arrayOf(1, 2, 3, 4, 5, 6, 7, 8)

		this.layout = BorderLayout()
		logcatPane = JTextPane()
		logcatPane.isEditable = false
		logcatScroll = JScrollPane(logcatPane)
		val menuPanel = JToolBar()

		val procObj = CheckCombo(NLS.str("logcat.process"), 1, msgIndex, pkgs.toTypedArray())
		val procBox = procObj.content
		procObj.selectAllBut(pids.indexOf(pid))

		val msgTypeBox = CheckCombo(NLS.str("logcat.level"), 2, msgIndex, msgTypes).content

		menuPanel.add(procBox)
		menuPanel.add(Box.createRigidArea(Dimension(5, 0)))
		menuPanel.add(msgTypeBox)
		menuPanel.add(Box.createRigidArea(Dimension(5, 0)))
		menuPanel.add(pauseButton)
		menuPanel.add(Box.createRigidArea(Dimension(5, 0)))
		menuPanel.add(clearButton)

		this.add(menuPanel, BorderLayout.NORTH)
		this.add(logcatScroll, BorderLayout.CENTER)

		return true
	}

	/** 清空日志区。 */
	fun clearLogcatArea(): Boolean {
		logcatPane.setText("")
		return true
	}

	/** 初始化：连接设备、拉取进程列表并展示 Logcat。 */
	fun init(device: ADBDevice, pid: String): Boolean {
		this.pid = pid.toInt()
		try {
			this.logcatController = LogcatController(this, device)
			this.procs = device.processList
			if (!this.showLogcat()) {
				debugPanel.log(NLS.str("logcat.error_fail_start"))
			}
		} catch (e: Exception) {
			this.ready = false
			LOG.error("Failed to start logcat", e)
			return false
		}
		this.ready = true
		return true
	}

	private fun toggleLogcat() {
		if (logcatController.getStatus() == "running") {
			logcatController.stopLogcat()
			pauseButton.putValue(Action.SMALL_ICON, ICON_RUN)
			pauseButton.putValue(Action.NAME, NLS.str("logcat.start"))
		} else if (logcatController.getStatus() == "stopped") {
			logcatController.startLogcat()
			pauseButton.putValue(Action.SMALL_ICON, ICON_PAUSE)
			pauseButton.putValue(Action.NAME, NLS.str("logcat.pause"))
		}
	}

	private fun clearLogcat() {
		var running = false
		if (logcatController.getStatus() == "running") {
			logcatController.stopLogcat()
			running = true
		}
		logcatController.clearLogcat()
		clearLogcatArea()
		debugPanel.log(logcatController.getStatus())
		if (running) {
			logcatController.startLogcat()
		}
	}

	/** Logcat 是否已就绪。 */
	val isReady: Boolean get() = this.ready

	private fun isAtBottom(scrollbar: JScrollBar): Boolean {
		val model: BoundedRangeModel = scrollbar.getModel()
		return (model.getExtent() + model.getValue()) == model.getMaximum()
	}

	/** 追加一条日志；未知级别（0）直接忽略。 */
	fun log(logcatInfo: LogcatController.LogcatInfo) {
		val len = logcatPane.getDocument().getLength()
		val scrollbar = logcatScroll.getVerticalScrollBar()
		val atBottom = isAtBottom(scrollbar)

		val logString = " > " + logcatInfo.timestamp + " [pid: " + logcatInfo.getPid() + "] " +
			logcatInfo.msgTypeString + ": " + logcatInfo.getMsg() + "\n"

		if (logcatInfo.getMsgType().toInt() == 0) {
			return // ignore unknown
		}

		val attrSet = asetMap[logcatInfo.getMsgType()]

		UiUtils.uiRun {
			try {
				logcatPane.getDocument().insertString(len, logString, attrSet)
			} catch (e: Exception) {
				LOG.error("Failed to add logcat message", e)
			}
			if (atBottom) {
				EventQueue.invokeLater { scrollbar.setValue(scrollbar.getMaximum()) }
			}
		}
	}

	/** 退出 Logcat：停止控制器并清空日志。 */
	fun exit() {
		logcatController.exit()
		clearLogcatArea()
		logcatController.clearEvents()
	}

	/**
	 * 可多选的过滤下拉框。
	 *
	 * 下拉项带复选框，点击项时切换选中状态并同步到 [LogcatController] 的过滤器。
	 */
	inner class CheckCombo(
		val label: String,
		val type: Int,
		val index: Array<Int>,
		val ids: Array<String>,
	) : ActionListener {
		private lateinit var combo: JComboBox<CheckComboStore>

		override fun actionPerformed(e: ActionEvent) {
			val cb = e.getSource() as JComboBox<*>
			val store = cb.getSelectedItem() as CheckComboStore
			val ccr = cb.getRenderer() as CheckComboRenderer
			store.state = !store.state
			ccr.checkBox.setSelected(store.state)

			when (this.type) {
				1 -> { // process
					logcatController.getFilter().togglePid(store.index, store.state)
					logcatController.reload()
				}

				2 -> { // label
					logcatController.getFilter().toggleMsgType(store.index.toByte(), store.state)
					logcatController.reload()
				}

				else -> LOG.error("Invalid Logcat Filter Type")
			}
		}

		val content: JPanel get() {
			val labelComp = NodeLabel.noHtml("$label: ")
			val stores = Array(ids.size) { j -> CheckComboStore(index[j], ids[j], true) }
			combo = JComboBox(stores)
			combo.setRenderer(CheckComboRenderer())
			val panel = JPanel()
			panel.layout = GridBagLayout()
			val c = GridBagConstraints()
			c.weightx = 0.0
			c.gridwidth = 1
			c.insets = Insets(0, 1, 0, 1)
			panel.add(labelComp, c)
			c.weightx = 1.0
			c.gridwidth = GridBagConstraints.REMAINDER
			c.anchor = GridBagConstraints.WEST
			c.insets = Insets(0, 1, 0, 1)
			panel.add(combo, c)
			combo.addActionListener(this)
			combo.addMouseListener(FilterClickListener(this))
			return panel
		}

		fun toggleAll(checked: Boolean) {
			for (i in 0 until combo.getItemCount()) {
				val ccs = combo.getItemAt(i)
				ccs.state = checked
				when (type) {
					1 -> logcatController.getFilter().togglePid(ccs.index, checked)

					// process
					2 -> logcatController.getFilter().toggleMsgType(ccs.index.toByte(), checked)

					// level
					else -> LOG.error("Invalid Logcat Toggle Filter Encountered")
				}
			}
			logcatController.reload()
		}

		fun selectAllBut(ind: Int) {
			for (i in 0 until combo.getItemCount()) {
				val ccs = combo.getItemAt(i)
				ccs.state = (i == ind)
				when (type) {
					1 -> logcatController.getFilter().togglePid(ccs.index, ccs.state)

					// process
					2 -> logcatController.getFilter().toggleMsgType(ccs.index.toByte(), ccs.state)

					// level
					else -> LOG.error("Invalid Logcat selectAllBut filter encountered")
				}
			}
			logcatController.reload()
		}
	}

	private class CheckComboRenderer : ListCellRenderer<CheckComboStore> {
		val checkBox = JCheckBox()

		override fun getListCellRendererComponent(
			list: JList<out CheckComboStore>?,
			value: CheckComboStore,
			index: Int,
			isSelected: Boolean,
			cellHasFocus: Boolean,
		): Component {
			checkBox.setText(value.id)
			checkBox.setSelected(value.state)
			return checkBox
		}
	}

	internal class CheckComboStore(var index: Int, var id: String, var state: Boolean)

	inner class FilterClickListener(private val combo: CheckCombo) : MouseAdapter() {
		override fun mousePressed(e: MouseEvent) {
			if (e.isPopupTrigger()) {
				doPop(e)
			}
		}

		override fun mouseReleased(e: MouseEvent) {
			if (e.isPopupTrigger()) {
				doPop(e)
			}
		}

		private fun doPop(e: MouseEvent) {
			val menu = FilterPopup(combo)
			menu.show(e.getComponent(), e.getX(), e.getY())
		}
	}

	/** 过滤下拉框的右键菜单：全选 / 全不选 / 只选当前进程。 */
	inner class FilterPopup(private val combo: CheckCombo) : JPopupMenu() {
		private val selectAll: JMenuItem
		private val unselectAll: JMenuItem

		init {
			selectAll = JMenuItem(NLS.str("logcat.select_all"))
			selectAll.addActionListener { combo.toggleAll(true) }

			unselectAll = JMenuItem(NLS.str("logcat.unselect_all"))
			unselectAll.addActionListener { combo.toggleAll(false) }

			if (combo.type == 1) {
				val selectAttached = JMenuItem(NLS.str("logcat.select_attached"))
				selectAttached.addActionListener { combo.selectAllBut(pids.indexOf(pid)) }
				add(selectAttached)
			}

			add(selectAll)
			add(unselectAll)
		}
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(LogcatPanel::class.java)

		private val ICON_PAUSE: ImageIcon = UiUtils.openSvgIcon("debugger/threadFrozen")
		private val ICON_RUN: ImageIcon = UiUtils.openSvgIcon("debugger/execute")
		private val CLEAR_LOGCAT: ImageIcon = UiUtils.openSvgIcon("debugger/trash")
	}
}
