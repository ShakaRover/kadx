package jadx.gui.ui.dialog

import jadx.commons.app.JadxSystemInfo
import jadx.core.utils.StringUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.device.debugger.DbgUtils
import jadx.gui.device.debugger.DebugSettings
import jadx.gui.device.protocol.ADB
import jadx.gui.device.protocol.ADBDevice
import jadx.gui.device.protocol.ADBDeviceInfo
import jadx.gui.settings.JadxSettings
import jadx.gui.ui.MainWindow
import jadx.gui.ui.panel.IDebugController
import jadx.gui.utils.NLS
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.Label
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.net.Socket
import java.util.Collections
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * ADB 设备/进程选择对话框：用于连接 adb、列出设备与进程并启动调试。
 *
 * **做什么**：监听设备状态与 JDWP 进程变化，把设备/进程展示为树；
 * 双击进程即尝试附加调试器。
 *
 * **为什么保留 Swing 线程模型**：网络监听在回调线程触发，UI 更新统一回到 EDT，
 * 不引入协程。
 */
class ADBDialog(private val mainWindow: MainWindow) :
	JDialog(mainWindow),
	ADB.DeviceStateListener,
	ADB.JDWPProcessListener {

	private lateinit var tipLabel: Label
	private lateinit var pathTextField: JTextField
	private lateinit var hostTextField: JTextField
	private lateinit var portTextField: JTextField
	private lateinit var procTreeModel: DefaultTreeModel
	private lateinit var procTreeRoot: DefaultMutableTreeNode
	private lateinit var procTree: JTree
	private var deviceSocket: Socket? = null
	private var deviceNodes: MutableList<DeviceNode> = ArrayList()
	private var lastSelectedDeviceNode: DeviceNode? = null

	init {
		initUI()
		pathTextField.setText(mainWindow.getSettings().adbDialogPath)
		hostTextField.setText(mainWindow.getSettings().adbDialogHost)
		portTextField.setText(mainWindow.getSettings().adbDialogPort)

		if (pathTextField.getText().isEmpty()) {
			detectADBPath()
		} else {
			pathTextField.setText("")
		}

		SwingUtilities.invokeLater { connectToADB() }
		UiUtils.addEscapeShortCutToDispose(this)
	}

	private fun initUI() {
		pathTextField = JTextField()
		portTextField = JTextField()
		hostTextField = JTextField()

		val adbPanel = JPanel(BorderLayout(5, 5))
		adbPanel.add(JLabel(NLS.str("adb_dialog.path")), BorderLayout.WEST)
		adbPanel.add(pathTextField, BorderLayout.CENTER)

		val portPanel = JPanel(BorderLayout(5, 0))
		portPanel.add(JLabel(NLS.str("adb_dialog.port")), BorderLayout.WEST)
		portPanel.add(portTextField, BorderLayout.CENTER)

		val hostPanel = JPanel(BorderLayout(5, 0))
		hostPanel.add(JLabel(NLS.str("adb_dialog.addr")), BorderLayout.WEST)
		hostPanel.add(hostTextField, BorderLayout.CENTER)

		val wrapperPanel = JPanel(GridLayout(1, 2, 5, 0))
		wrapperPanel.add(hostPanel)
		wrapperPanel.add(portPanel)
		adbPanel.add(wrapperPanel, BorderLayout.SOUTH)

		procTree = JTree()
		val scrollPane = JScrollPane(procTree)
		scrollPane.setMinimumSize(Dimension(100, 150))
		scrollPane.setBorder(BorderFactory.createLineBorder(Color.black))

		procTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)
		procTreeRoot = DefaultMutableTreeNode(NLS.str("adb_dialog.device_node"))
		procTreeModel = DefaultTreeModel(procTreeRoot)
		procTree.setModel(procTreeModel)
		procTree.setRowHeight(-1)
		procTree.setFont(mainWindow.getSettings().codeFont)

		procTree.addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (e.getClickCount() == 2) {
					processSelected(e)
				}
			}
		})
		procTree.setCellRenderer(object : DefaultTreeCellRenderer() {
			override fun getTreeCellRendererComponent(
				tree: JTree,
				value: Any?,
				selected: Boolean,
				expanded: Boolean,
				leaf: Boolean,
				row: Int,
				hasFocus: Boolean,
			): Component {
				val c = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
				if (value is DeviceTreeNode || value === procTreeRoot) {
					setIcon(ICON_DEVICE)
				} else {
					setIcon(ICON_PROCESS)
				}
				return c
			}
		})

		procTree.addTreeSelectionListener {
			val selectedNode = procTree.getLastSelectedPathComponent()
			if (selectedNode is DeviceTreeNode) {
				lastSelectedDeviceNode = deviceNodes
					.firstOrNull { item -> item.tNode === selectedNode }
			}
		}

		val btnPane = JPanel()
		val boxLayout = BoxLayout(btnPane, BoxLayout.LINE_AXIS)
		btnPane.setLayout(boxLayout)
		tipLabel = Label(NLS.str("adb_dialog.waiting"))
		btnPane.add(tipLabel)
		val refreshBtn = JButton(NLS.str("adb_dialog.refresh"))
		val startServerBtn = JButton(NLS.str("adb_dialog.start_server"))
		val launchAppBtn = JButton(NLS.str("adb_dialog.launch_app"))
		btnPane.add(launchAppBtn)
		btnPane.add(startServerBtn)
		btnPane.add(refreshBtn)
		refreshBtn.addActionListener {
			clear()
			procTreeRoot.removeAllChildren()
			procTreeModel.reload(procTreeRoot)
			SwingUtilities.invokeLater { connectToADB() }
		}

		startServerBtn.addActionListener { startADBServer() }
		launchAppBtn.addActionListener { launchApp() }

		val mainPane = JPanel(BorderLayout(5, 5))
		mainPane.add(adbPanel, BorderLayout.NORTH)
		mainPane.add(scrollPane, BorderLayout.CENTER)
		mainPane.add(btnPane, BorderLayout.SOUTH)
		mainPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))

		getContentPane().add(mainPane)

		pack()
		setSize(800, 500)
		setLocationRelativeTo(null)
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
		setModalityType(ModalityType.MODELESS)
	}
	private fun clear() {
		val socket = deviceSocket
		if (socket != null) {
			try {
				socket.close()
			} catch (e: Exception) {
				LOG.error("Failed to close device socket", e)
			}
			deviceSocket = null
		}
		for (deviceNode in deviceNodes) {
			deviceNode.device.stopListenForJDWP()
		}
		deviceNodes.clear()
	}

	private fun detectADBPath() {
		val isWinOS = JadxSystemInfo.IS_WINDOWS
		val slash = if (isWinOS) "\\" else "/"
		val adbName = if (isWinOS) "adb.exe" else "adb"
		val sdkPathEnv = System.getenv("ANDROID_HOME")
		if (!StringUtils.isEmpty(sdkPathEnv)) {
			var sdkPath = checkNotNull(sdkPathEnv)
			if (!sdkPath.endsWith(slash)) {
				sdkPath += slash
			}
			sdkPath += "platform-tools" + slash + adbName
			if (File(sdkPath).exists()) {
				pathTextField.setText(sdkPath)
				return
			}
		}
		val envPath = checkNotNull(System.getenv("PATH"))
		val paths = envPath.split(if (isWinOS) ";" else ":")
		for (p in paths) {
			var path = p
			if (!path.endsWith(slash)) {
				path += slash
			}
			path += adbName
			if (File(path).exists()) {
				pathTextField.setText(path)
				return
			}
		}
	}

	private fun startADBServer() {
		val path = pathTextField.getText()
		if (path.isEmpty()) {
			UiUtils.showMessageBox(mainWindow, NLS.str("adb_dialog.missing_path"))
			return
		}
		var tip: String? = null
		try {
			tip = if (ADB.startServer(path, portTextField.getText().toInt())) {
				NLS.str("adb_dialog.start_okay", portTextField.getText())
			} else {
				NLS.str("adb_dialog.start_fail", portTextField.getText())
			}
		} catch (e: Exception) {
			LOG.error("Failed to start adb server", e)
			tip = e.message
		}
		UiUtils.showMessageBox(mainWindow, tip ?: "")
		tipLabel.setText(tip)
	}

	private fun connectToADB() {
		var tip: String? = null
		try {
			val host = hostTextField.getText().trim()
			val port = portTextField.getText().trim()
			tipLabel.setText(NLS.str("adb_dialog.connecting", host, port))
			deviceSocket = ADB.listenForDeviceState(this, host, port.toInt())
			tip = if (deviceSocket != null) {
				val okTip = NLS.str("adb_dialog.connect_okay", host, port)
				this.title = okTip
				okTip
			} else {
				NLS.str("adb_dialog.connect_fail")
			}
		} catch (e: Exception) {
			LOG.error("Failed to connect to adb", e)
			tip = e.message
			UiUtils.showMessageBox(mainWindow, tip ?: "")
		}
		tipLabel.setText(tip)
	}
	override fun onDeviceStatusChange(deviceInfoList: List<ADBDeviceInfo>) {
		LOG.debug("onDeviceStatusChange {}", deviceInfoList)
		val nodes = ArrayList<DeviceNode>(deviceInfoList.size)
		info_loop@ for (info in deviceInfoList) {
			for (deviceNode in deviceNodes) {
				if (deviceNode.device.updateDeviceInfo(info)) {
					deviceNode.refresh()
					nodes.add(deviceNode)
					continue@info_loop
				}
			}
			val device = ADBDevice(info)
			device.androidReleaseVersion
			nodes.add(DeviceNode(device))
			listenJDWP(device)
		}
		deviceNodes = nodes
		SwingUtilities.invokeLater {
			tipLabel.setText(NLS.str("adb_dialog.tip_devices", deviceNodes.size))
			procTreeRoot.removeAllChildren()
			deviceNodes.forEach { n -> procTreeRoot.add(n.tNode) }
			procTreeModel.reload(procTreeRoot)
			for (deviceNode in deviceNodes) {
				procTree.expandPath(TreePath(deviceNode.tNode.getPath()))
			}
		}
	}

	private fun processSelected(e: MouseEvent) {
		val path = procTree.getPathForLocation(e.getX(), e.getY()) ?: return
		val node = path.getLastPathComponent() as DefaultMutableTreeNode
		val pid = getPid(node.getUserObject() as String) ?: return
		if (StringUtils.isEmpty(pid)) {
			return
		}
		val debuggerPanel = mainWindow.getDebuggerPanel()
		if (debuggerPanel != null && debuggerPanel.dbgController.isDebugging()) {
			if (JOptionPane.showConfirmDialog(
					mainWindow,
					NLS.str("adb_dialog.restart_while_debugging_msg"),
					NLS.str("adb_dialog.restart_while_debugging_title"),
					JOptionPane.OK_CANCEL_OPTION,
				) != JOptionPane.CANCEL_OPTION
			) {
				val ctrl: IDebugController = debuggerPanel.dbgController
				if (launchForDebugging(mainWindow, ctrl.getProcessName(), true)) {
					dispose()
				}
			}
			return
		}
		val deviceNode = getDeviceNode(node.getParent() as DefaultMutableTreeNode) ?: return
		if (!setupArgs(deviceNode.device, pid, node.getUserObject() as String)) {
			return
		}
		if (DebugSettings.INSTANCE.isBeingDebugged) {
			if (JOptionPane.showConfirmDialog(
					mainWindow,
					NLS.str("adb_dialog.being_debugged_msg"),
					NLS.str("adb_dialog.being_debugged_title"),
					JOptionPane.OK_CANCEL_OPTION,
				) == JOptionPane.CANCEL_OPTION
			) {
				return
			}
		}
		tipLabel.setText(NLS.str("adb_dialog.starting_debugger"))
		if (!attachProcess(mainWindow)) {
			tipLabel.setText(NLS.str("adb_dialog.init_dbg_fail"))
		} else {
			dispose()
		}
	}
	private fun getPid(nodeText: String): String? {
		if (nodeText.startsWith("[pid:")) {
			val pos = nodeText.indexOf(']', "[pid:".length)
			if (pos != -1) {
				return nodeText.substring("[pid:".length, pos).trim()
			}
		}
		return null
	}

	private fun getDeviceNode(node: DefaultMutableTreeNode): DeviceNode? {
		for (deviceNode in deviceNodes) {
			if (deviceNode.tNode === node) {
				return deviceNode
			}
		}
		return null
	}

	private fun getDeviceNode(device: ADBDevice): DeviceNode {
		for (deviceNode in deviceNodes) {
			if (deviceNode.device == device) {
				return deviceNode
			}
		}
		throw JadxRuntimeException("Unexpected device: " + device)
	}

	private fun listenJDWP(device: ADBDevice) {
		try {
			device.listenForJDWP(this)
		} catch (e: Exception) {
			LOG.error("Failed listen for JDWP", e)
		}
	}

	override fun dispose() {
		clear()
		val settings: JadxSettings = mainWindow.getSettings()
		var changed = settings.adbDialogPath != pathTextField.getText()
		changed = changed or (settings.adbDialogHost != hostTextField.getText())
		changed = changed or (settings.adbDialogPort != portTextField.getText())
		if (changed) {
			settings.setAdbDialogPath(pathTextField.getText())
			settings.setAdbDialogHost(hostTextField.getText())
			settings.setAdbDialogPort(portTextField.getText())
			settings.sync()
		}
		super.dispose()
	}

	override fun adbDisconnected() {
		deviceSocket = null
		SwingUtilities.invokeLater {
			tipLabel.setText(NLS.str("adb_dialog.disconnected"))
			this.title = ""
		}
	}

	override fun jdwpProcessOccurred(device: ADBDevice, id: MutableSet<String>) {
		var procs: List<ADB.Process> = Collections.emptyList()
		try {
			Thread.sleep(40)
			/*
			 * 稍等片刻，让远端的新进程完全初始化，
			 * 否则可能拿不到真实进程名而是 <pre-initialized> 状态。
			 */
			procs = device.processList
		} catch (e: Exception) {
			LOG.error("Failed to get device process list", e)
			procs = Collections.emptyList()
		}
		val procList = ArrayList<String>(id.size)
		if (procs.isEmpty()) {
			procList.addAll(id)
		} else {
			for (proc in procs) {
				if (id.contains(proc.pid)) {
					procList.add(String.format("[pid: %-6s] %s", proc.pid, proc.name))
				}
			}
		}
		Collections.reverse(procList)
		val node: DeviceNode
		try {
			node = getDeviceNode(device)
		} catch (e: Exception) {
			LOG.error("Failed to find device", e)
			return
		}

		SwingUtilities.invokeLater {
			node.tNode.removeAllChildren()
			var foundNode: DefaultMutableTreeNode? = null
			val debugSettings = DebugSettings.INSTANCE
			for (procStr in procList) {
				val pnode = DefaultMutableTreeNode(procStr)
				node.tNode.add(pnode)
				if (debugSettings.getExpectPkg().isNotEmpty() && procStr.endsWith(debugSettings.getExpectPkg())) {
					if (debugSettings.isAutoAttachPkg && debugSettings.getDevice() == node.device) {
						debugSettings.set(node.device, debugSettings.getVer(), getPid(procStr), procStr)
						if (attachProcess(mainWindow)) {
							dispose()
							return@invokeLater
						}
					}
					foundNode = pnode
				}
			}
			procTreeModel.reload(node.tNode)
			procTree.expandPath(TreePath(node.tNode.getPath()))
			if (foundNode != null) {
				val thePath = TreePath(foundNode.getPath())
				procTree.scrollPathToVisible(thePath)
				procTree.setSelectionPath(thePath)
			}
		}
	}
	private fun launchApp() {
		if (deviceNodes.isEmpty()) {
			UiUtils.showMessageBox(mainWindow, NLS.str("adb_dialog.no_devices"))
			return
		}
		val appData = DbgUtils.parseAppData(mainWindow) ?: return
		if (scrollToProcNode(appData.getAppPackage())) {
			return
		}
		val processName = appData.processName
		val lastNode = lastSelectedDeviceNode
		val device = if (lastNode == null) deviceNodes[0].device else lastNode.device
		try {
			device.launchApp(processName)
		} catch (e: Exception) {
			LOG.error("Failed to launch app: {}", processName, e)
			UiUtils.showMessageBox(mainWindow, e.message ?: "")
		}
	}

	private fun scrollToProcNode(pkg: String): Boolean {
		if (pkg.isEmpty()) {
			return false
		}
		DebugSettings.INSTANCE.setExpectPkg(" " + pkg)
		for (i in 0 until procTreeRoot.getChildCount()) {
			val rn = procTreeRoot.getChildAt(i) as DefaultMutableTreeNode
			for (j in 0 until rn.getChildCount()) {
				val n = rn.getChildAt(j) as DefaultMutableTreeNode
				val pName = n.getUserObject() as String
				if (pName.endsWith(DebugSettings.INSTANCE.getExpectPkg())) {
					val path = TreePath(n.getPath())
					procTree.scrollPathToVisible(path)
					procTree.setSelectionPath(path)
					return true
				}
			}
		}
		return false
	}

	override fun jdwpListenerClosed(device: ADBDevice) {
		// 无需处理
	}

	private fun setupArgs(device: ADBDevice, pid: String, name: String): Boolean {
		var ver = device.androidReleaseVersion
		if (StringUtils.isEmpty(ver)) {
			if (JOptionPane.showConfirmDialog(
					mainWindow,
					NLS.str("adb_dialog.unknown_android_ver"),
					"",
					JOptionPane.OK_CANCEL_OPTION,
				) == JOptionPane.CANCEL_OPTION
			) {
				return false
			}
			ver = "8"
		}
		ver = getMajorVer(ver)
		DebugSettings.INSTANCE.set(device, ver.toInt(), pid, name)
		return true
	}

	private fun getMajorVer(ver: String): String {
		var major = ver
		val pos = major.indexOf('.')
		if (pos != -1) {
			major = major.substring(0, pos)
		}
		return major
	}
	private class DeviceTreeNode : DefaultMutableTreeNode() {
		companion object {
			private const val serialVersionUID = -1111111202103131112L
		}
	}

	private class DeviceNode(adbDevice: ADBDevice) {
		var device: ADBDevice = adbDevice
		var tNode: DeviceTreeNode = DeviceTreeNode()

		init {
			refresh()
		}

		fun refresh() {
			val info = device.deviceInfo
			var text = info.getModel()
			if (text != null) {
				if (text != info.getSerial()) {
					text += " [serial: ${info.getSerial()}]"
				}
				text += " [state: ${if (info.isOnline) "online" else "offline"}]"
				tNode.setUserObject(text)
			}
		}
	}
	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ADBDialog::class.java)
		private const val serialVersionUID = -1111111202102181630L
		private val ICON_DEVICE: ImageIcon = UiUtils.openSvgIcon("adb/androidDevice")
		private val ICON_PROCESS: ImageIcon = UiUtils.openSvgIcon("adb/addToWatch")

		private fun attachProcess(mainWindow: MainWindow): Boolean {
			val debugSettings = DebugSettings.INSTANCE
			debugSettings.clearForward()
			val rst = debugSettings.forwardJDWP()
			if (rst.isNotEmpty()) {
				UiUtils.showMessageBox(mainWindow, rst)
				return false
			}
			try {
				return mainWindow.getDebuggerPanel().showDebugger(
					debugSettings.getName(),
					debugSettings.getDevice().deviceInfo.getAdbHost(),
					debugSettings.getForwardTcpPort(),
					debugSettings.getVer(),
					debugSettings.getDevice(),
					debugSettings.getPid(),
				)
			} catch (e: Exception) {
				LOG.error("Failed to attach to process", e)
				return false
			}
		}

		fun launchForDebugging(mainWindow: MainWindow, fullAppPath: String, autoAttach: Boolean): Boolean {
			val debugSettings = DebugSettings.INSTANCE
			debugSettings.setAutoAttachPkg(autoAttach)
			try {
				val pid = debugSettings.getDevice().launchApp(fullAppPath)
				if (pid != -1) {
					debugSettings.setPid(pid.toString()).setName(fullAppPath)
					return attachProcess(mainWindow)
				}
			} catch (e: Exception) {
				LOG.error("Failed to launch app", e)
			}
			return false
		}
	}
}
