package jadx.gui.device.debugger

import jadx.core.utils.StringUtils
import jadx.gui.device.protocol.ADB
import jadx.gui.device.protocol.ADBDevice
import jadx.gui.utils.NLS
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 调试会话的全局设置（单例）。
 *
 * **做什么**：保存当前要调试的设备、Android 版本、进程 pid / 名称，
 * 并负责 ADB 端口转发（把设备的 JDWP 端口转发到本机）与清理。
 *
 * **为什么用 `INSTANCE` 单例**：原 Java 通过 `public static final INSTANCE` 暴露，
 * GUI 各处直接引用 `DebugSettings.INSTANCE`，这里用 companion 的 `@JvmField val` 保持。
 */
class DebugSettings private constructor() {

	/** Android 主版本号。 */
	private var ver: Int = 0

	/** 目标进程 pid（可能尚未设置，故为可空）。 */
	private var pid: String? = null

	/** 目标进程名。 */
	private var name: String? = null

	/** 目标设备。 */
	private var device: ADBDevice? = null

	/** 本机转发端口，默认 [FORWARD_TCP_PORT]。 */
	private var forwardTcpPort: Int = FORWARD_TCP_PORT

	/** 期望自动附加的包名（带前导空格用于后缀匹配）。 */
	private var expectPkg: String = ""

	/** 是否自动附加到新出现的进程。 */
	private var autoAttachPkg: Boolean = false

	/** 设置本次调试的设备与目标进程。 */
	fun set(device: ADBDevice, ver: Int, pid: String?, name: String?) {
		this.ver = ver
		this.pid = pid
		this.name = name
		this.device = device
		this.autoAttachPkg = false
		this.expectPkg = ""
	}

	fun setPid(pid: String): DebugSettings {
		this.pid = pid
		return this
	}

	fun setName(name: String): DebugSettings {
		this.name = name
		return this
	}

	/**
	 * 把设备上的 `jdwp:pid` 转发到本机 TCP 端口。
	 *
	 * 若端口被占用则自动递增重试，直到成功或遇到不可重试的错误。
	 *
	 * @return 空字符串表示成功；否则返回失败描述
	 */
	fun forwardJDWP(): String {
		var localPort = forwardTcpPort
		var resultDesc = ""
		try {
			do {
				val rst = checkNotNull(device).forwardJDWP(localPort.toString(), checkNotNull(pid))
				if (rst.state == 0) {
					forwardTcpPort = localPort
					return ""
				}
				if (rst.state == 1) {
					if (rst.desc.contains("Only one usage of each socket address")) { // 端口被其它进程占用
						if (localPort < 65536) {
							localPort++ // 重试
							continue
						}
					}
				}
				resultDesc = rst.desc
				break
			} while (true)
		} catch (e: Exception) {
			LOG.error("JDWP forward error", e)
		}
		if (StringUtils.isEmpty(resultDesc)) {
			resultDesc = NLS.str("adb_dialog.forward_fail")
		}
		return resultDesc
	}

	/**
	 * 移除所有转发到当前 `jdwp:pid` 的端口，否则 JDWP 握手可能失败。
	 */
	fun clearForward() {
		val jdwpPid = " jdwp:$pid"
		val tcpPort = " tcp:$forwardTcpPort"
		try {
			val dev = checkNotNull(device)
			val list = ADB.listForward(dev.deviceInfo.getAdbHost(), dev.deviceInfo.getAdbPort())
			for (s in list) {
				if (s.startsWith(dev.serial) && s.endsWith(jdwpPid) && !s.contains(tcpPort)) {
					val fields = s.split(Regex("\\s+"))
					for (field in fields) {
						if (field.startsWith("tcp:")) {
							try {
								dev.removeForward(field.substring("tcp:".length))
							} catch (e: Exception) {
								LOG.error("JDWP remove forward error", e)
							}
						}
					}
				}
			}
		} catch (e: Exception) {
			LOG.error("JDWP clear forward error", e)
		}
	}

	/** @return 当前 `jdwp:pid` 是否已被其它端口转发（即正在被调试） */
	val isBeingDebugged: Boolean get() {
		val jdwpPid = " jdwp:$pid"
		val tcpPort = " tcp:$forwardTcpPort"
		try {
			val dev = checkNotNull(device)
			val list = ADB.listForward(dev.deviceInfo.getAdbHost(), dev.deviceInfo.getAdbPort())
			for (s in list) {
				if (s.startsWith(dev.serial) && s.endsWith(jdwpPid)) {
					return !s.contains(tcpPort)
				}
			}
		} catch (e: Exception) {
			LOG.error("ADB list forward error", e)
		}
		return false
	}

	fun getVer(): Int = ver

	fun getPid(): String = checkNotNull(pid)

	fun getName(): String = checkNotNull(name)

	fun getDevice(): ADBDevice = checkNotNull(device)

	fun getForwardTcpPort(): Int = forwardTcpPort

	fun getExpectPkg(): String = expectPkg

	fun setExpectPkg(expectPkg: String) {
		this.expectPkg = expectPkg
	}

	val isAutoAttachPkg: Boolean get() = autoAttachPkg

	fun setAutoAttachPkg(autoAttachPkg: Boolean) {
		this.autoAttachPkg = autoAttachPkg
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DebugSettings::class.java)

		private const val FORWARD_TCP_PORT = 33233

		val INSTANCE: DebugSettings = DebugSettings()
	}
}
