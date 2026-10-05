package kadx.gui.device.protocol

import kadx.core.utils.StringUtils
import kadx.core.utils.log.LogUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.regex.Pattern

/**
 * 一个已连接的 ADB 设备。
 *
 * **做什么**：封装对单个设备的常用操作：端口转发、启动应用、读取 logcat、
 * 读取系统属性、枚举进程，以及监听可调试（JDWP）进程。
 *
 * **线程模型**：阻塞 socket I/O 与后台 `Thread`。
 */
class ADBDevice(private var info: ADBDeviceInfo) {
	/** Android 版本号缓存。 */
	private var androidReleaseVer: String? = null

	/** JDWP 监听 socket；`volatile` 保证多线程可见性。 */
	@Volatile
	private var jdwpListenerSock: Socket? = null

	/** @return 设备元信息 */
	val deviceInfo: ADBDeviceInfo get() = info

	/** 用新信息更新设备；序列号不匹配时返回 false。 */
	fun updateDeviceInfo(info: ADBDeviceInfo): Boolean {
		if (info.getSerial().isEmpty()) {
			return false
		}
		val matched = this.info.getSerial() == info.getSerial()
		if (matched) {
			this.info = info
		}
		return matched
	}

	/** @return 设备序列号 */
	val serial: String get() = info.getSerial()

	/** 移除本机端口转发。 */
	@Throws(IOException::class)
	fun removeForward(localPort: String): Boolean = ADB.removeForward(info.getAdbHost(), info.getAdbPort(), info.getSerial(), localPort)

	/** 把设备的 `jdwp:pid` 转发到本机 [localPort]。 */
	@Throws(IOException::class)
	fun forwardJDWP(localPort: String, jdwpPid: String): ForwardResult {
		ADB.connect(info.getAdbHost(), info.getAdbPort()).use { socket ->
			var cmd = "host:forward:tcp:$localPort;jdwp:$jdwpPid"
			cmd = String.format("%04x%s", cmd.length, cmd)
			val inputStream = socket.getInputStream()
			val outputStream = socket.getOutputStream()
			val rst: ForwardResult
			if (ADB.setSerial(info.getSerial(), outputStream, inputStream)) {
				outputStream.write(cmd.toByteArray(ADB.ADB_CHARSET))
				rst = if (!ADB.isOkay(inputStream, "forwardJDWP1")) {
					ForwardResult(1, ADB.readServiceProtocol(inputStream))
				} else if (!ADB.isOkay(inputStream, "forwardJDWP2")) {
					ForwardResult(2, ADB.readServiceProtocol(inputStream))
				} else {
					ForwardResult(0, null)
				}
			} else {
				rst = ForwardResult(1, "Unknown error.".toByteArray(ADB.ADB_CHARSET))
			}
			return rst
		}
	}

	/** 端口转发结果。 */
	class ForwardResult(state: Int, desc: ByteArray?) {
		/** 0 表示成功，1 表示本机 tcp 绑定失败，2 表示远端失败。 */
		var state: Int = state

		var desc: String = if (desc != null) String(desc, ADB.ADB_CHARSET) else ""
	}

	/**
	 * 以调试模式启动应用，并返回其 pid（失败返回 -1）。
	 */
	@Throws(IOException::class, InterruptedException::class)
	fun launchApp(fullAppName: String): Int {
		var res: ByteArray? = null
		ADB.connect(info.getAdbHost(), info.getAdbPort()).use { socket ->
			val cmd = "am start -D -n $fullAppName"
			res = ADB.execShellCommandRaw(info.getSerial(), cmd, socket.getOutputStream(), socket.getInputStream())
			if (res == null) {
				return -1
			}
		}
		val rst = String(checkNotNull(res), ADB.ADB_CHARSET).trim()
		if (rst.startsWith("Starting: Intent {") && rst.endsWith("$fullAppName }")) {
			Thread.sleep(40)
			val pkg = fullAppName.split("/")[0]
			for (process in getProcessByPkg(pkg)) {
				return checkNotNull(process.pid).toInt()
			}
		}
		return -1
	}

	/** @return 设备的二进制 logcat 输出 */
	@get:Throws(IOException::class)
	val binaryLogcat: ByteArray? get() {
		ADB.connect(info.getAdbHost(), info.getAdbPort()).use { socket ->
			val cmd = "logcat -dB"
			return ADB.execShellCommandRaw(info.getSerial(), cmd, socket.getOutputStream(), socket.getInputStream())
		}
	}

	/**
	 * @return 指定时间戳之后的二进制 logcat 输出；时间戳格式形如 `09-08 02:18:03.131`
	 */
	@Throws(IOException::class)
	fun getBinaryLogcat(timestamp: String): ByteArray? {
		ADB.connect(info.getAdbHost(), info.getAdbPort()).use { socket ->
			val matcher = TIMESTAMP_FORMAT.matcher(timestamp)
			if (!matcher.find()) {
				LOG.error("Invalid Logcat Timestamp {}", timestamp)
			}
			val cmd = "logcat -dB -t \"$timestamp\""
			return ADB.execShellCommandRaw(info.getSerial(), cmd, socket.getOutputStream(), socket.getInputStream())
		}
	}

	/** 清空设备 logcat 缓冲区。 */
	@Throws(IOException::class)
	fun clearLogcat() {
		ADB.connect(info.getAdbHost(), info.getAdbPort()).use { socket ->
			val cmd = "logcat -c"
			ADB.execShellCommandRaw(info.getSerial(), cmd, socket.getOutputStream(), socket.getInputStream())
		}
	}

	/** @return 设备时区 */
	@get:Throws(IOException::class)
	val timezone: String get() {
		ADB.connect(info.getAdbHost(), info.getAdbPort()).use { socket ->
			val cmd = "getprop persist.sys.timezone"
			val tz = ADB.execShellCommandRaw(info.getSerial(), cmd, socket.getOutputStream(), socket.getInputStream())
				?: throw IOException("Failed to get timezone")
			return String(tz, ADB.ADB_CHARSET).trim()
		}
	}

	/** @return 设备 Android 版本号 */
	val androidReleaseVersion: String get() {
		val cached = androidReleaseVer
		if (!StringUtils.isEmpty(cached)) {
			return checkNotNull(cached)
		}
		try {
			val list = getProp("ro.build.version.release")
			if (list.isNotEmpty()) {
				return list[0]
			}
			LOG.error("Failed to get android release version - no result")
		} catch (e: Exception) {
			LOG.error("Failed to get android release version", e)
			androidReleaseVer = ""
		}
		return androidReleaseVer ?: ""
	}

	/** 读取系统属性（[entry] 为空时返回全部）。 */
	@Throws(IOException::class)
	fun getProp(entry: String): List<String> {
		LOG.debug("ADB getProp({})", entry)
		ADB.connect(info.getAdbHost(), info.getAdbPort()).use { socket ->
			val props = ArrayList<String>()
			var cmd = "getprop"
			if (!StringUtils.isEmpty(entry)) {
				cmd += " $entry"
			}
			val payload = ADB.execShellCommandRaw(info.getSerial(), cmd, socket.getOutputStream(), socket.getInputStream())
			if (payload != null) {
				val lines = String(payload, ADB.ADB_CHARSET).split("\n")
				for (line in lines) {
					val trimmed = line.trim()
					if (trimmed.isNotEmpty()) {
						props.add(trimmed)
					}
				}
			}
			LOG.trace("ADB getProp({}) = {}", entry, props)
			return props
		}
	}

	/** 按包名筛选进程。 */
	@Throws(IOException::class)
	fun getProcessByPkg(pkg: String): List<ADB.Process> = getProcessList("ps | grep $pkg")

	/** @return 设备上全部进程 */
	@get:Throws(IOException::class)
	val processList: List<ADB.Process> get() = getProcessList("ps")

	@Throws(IOException::class)
	private fun getProcessList(cmd: String): List<ADB.Process> {
		ADB.connect(info.getAdbHost(), info.getAdbPort()).use { socket ->
			val procs = ArrayList<ADB.Process>()
			val payload = ADB.execShellCommandRaw(info.getSerial(), cmd, socket.getOutputStream(), socket.getInputStream())
			if (payload != null) {
				val ps = String(payload, ADB.ADB_CHARSET)
				val psLines = ps.split("\n")
				for (line in psLines) {
					val trimmed = line.trim()
					if (trimmed.isEmpty()) {
						continue
					}
					val proc = ADB.Process.make(trimmed)
					if (proc != null) {
						procs.add(proc)
					} else {
						LOG.error("Unexpected process info data received: \"{}\"", LogUtils.escape(trimmed))
					}
				}
			}
			return procs
		}
	}

	/**
	 * 开始监听设备上出现的可调试进程。
	 *
	 * @return 是否成功启动监听
	 */
	@Throws(IOException::class)
	fun listenForJDWP(listener: ADB.JDWPProcessListener?): Boolean {
		if (this.jdwpListenerSock != null) {
			return false
		}
		val sock = ADB.connect(info.getAdbHost(), info.getAdbPort())
		jdwpListenerSock = sock
		val inputStream = sock.getInputStream()
		val outputStream = sock.getOutputStream()
		if (ADB.setSerial(info.getSerial(), outputStream, inputStream) &&
			ADB.execCommandAsync(outputStream, inputStream, CMD_TRACK_JDWP)
		) {
			Thread {
				while (true) {
					val res = ADB.readServiceProtocol(inputStream)
					if (res != null) {
						if (listener != null) {
							val payload = String(res, ADB.ADB_CHARSET)
							val ids = payload.split("\n")
							val idList = HashSet<String>(ids.size)
							for (id in ids) {
								if (id.trim().isNotEmpty()) {
									idList.add(id)
								}
							}
							if (idList.isEmpty()) {
								LOG.info("No debuggable app process found on device {}", info.getSerial())
							}
							listener.jdwpProcessOccurred(this, idList)
						}
					} else { // socket 断开
						break
					}
				}
				if (listener != null) {
					this.jdwpListenerSock = null
					listener.jdwpListenerClosed(this)
				}
			}.start()
			return true
		} else {
			sock.close()
			jdwpListenerSock = null
			return false
		}
	}

	/** 停止监听可调试进程。 */
	fun stopListenForJDWP() {
		val sock = jdwpListenerSock
		if (sock != null) {
			try {
				sock.close()
			} catch (e: Exception) {
				LOG.error("JDWP socket close failed", e)
			}
		}
		this.jdwpListenerSock = null
	}

	override fun hashCode(): Int = info.getSerial().hashCode()

	override fun equals(other: Any?): Boolean {
		if (other is ADBDevice) {
			val otherSerial = other.deviceInfo.getSerial()
			return otherSerial == info.getSerial()
		}
		return false
	}

	override fun toString(): String = info.getAllInfo()

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ADBDevice::class.java)

		private const val CMD_TRACK_JDWP = "000atrack-jdwp"

		private val TIMESTAMP_FORMAT: Pattern =
			Pattern.compile("^[0-9]{2}\\-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}$")
	}
}
