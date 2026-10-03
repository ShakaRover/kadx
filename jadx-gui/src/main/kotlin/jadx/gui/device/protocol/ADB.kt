package jadx.gui.device.protocol

import jadx.core.utils.log.LogUtils
import jadx.gui.utils.IOUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.StringJoiner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * ADB（Android Debug Bridge）客户端协议实现。
 *
 * **做什么**：直接与 adb server 的 5037 端口通信，实现设备跟踪、端口转发、
 * 执行 shell 命令等能力；所有 socket I/O 均为阻塞式，保留原 Java 线程模型。
 *
 * **为什么是 `object`**：原 Java 全部是静态方法/常量，转成单例后
 * Kotlin 侧仍以 `ADB.xxx` 调用，`ADB.Process` 等嵌套类型也保持原路径。
 */
object ADB {

	@JvmField
	val ADB_CHARSET: Charset = StandardCharsets.UTF_8

	private val LOG: Logger = LoggerFactory.getLogger(ADB::class.java)

	private const val DEFAULT_PORT = 5037
	private const val DEFAULT_ADDR = "localhost"

	private const val CMD_FEATURES = "000dhost:features"
	private const val CMD_TRACK_DEVICES = "0014host:track-devices-l"
	private val OKAY: ByteArray = "OKAY".toByteArray(ADB_CHARSET)
	private val FAIL: ByteArray = "FAIL".toByteArray(ADB_CHARSET)

	internal fun isOkay(stream: InputStream, command: String): Boolean {
		val buf = IOUtils.readNBytes(stream, 4)
		if (Arrays.equals(buf, OKAY)) {
			return true
		}
		if (Arrays.equals(buf, FAIL)) {
			// 观察到 FAIL 之后会跟随十六进制长度与错误信息，但并非所有情况都如此。
			// int msgLen = Integer.parseInt(new String(IOUtils.readNBytes(stream, 4)), 16);
			// byte[] errorMsg = IOUtils.readNBytes(stream, msgLen);
			// LOG.error("isOkay failed: received error message: {}", new String(errorMsg));
			LOG.error("isOkay failed for command: {}", command)
			return false
		}
		if (buf == null) {
			throw IOException("isOkay failed - steam ended")
		}
		throw IOException("isOkay failed - unexpected response " + String(buf, ADB_CHARSET))
	}

	@Throws(IOException::class)
	fun exec(cmd: String, outputStream: OutputStream, inputStream: InputStream): ByteArray? = execCommandSync(outputStream, inputStream, cmd)

	@Throws(IOException::class)
	fun exec(cmd: String): ByteArray? = connect().use { socket ->
		exec(cmd, socket.getOutputStream(), socket.getInputStream())
	}

	@Throws(IOException::class)
	fun connect(): Socket = connect(DEFAULT_ADDR, DEFAULT_PORT)

	@Throws(IOException::class)
	fun connect(host: String, port: Int): Socket = Socket(host, port)

	internal fun execCommandAsync(outputStream: OutputStream, inputStream: InputStream, cmd: String): Boolean {
		outputStream.write(cmd.toByteArray(ADB_CHARSET))
		return isOkay(inputStream, "execCommandAsync")
	}

	@Throws(IOException::class)
	private fun execCommandSync(outputStream: OutputStream, inputStream: InputStream, cmd: String): ByteArray? {
		outputStream.write(cmd.toByteArray(ADB_CHARSET))
		if (isOkay(inputStream, "execCommandSync")) {
			return readServiceProtocol(inputStream)
		}
		return null
	}

	internal fun readServiceProtocol(stream: InputStream): ByteArray? {
		try {
			val buf = IOUtils.readNBytes(stream, 4) ?: return null
			val len = hexToInt(buf)
			val result: ByteArray = if (len == 0) {
				ByteArray(0)
			} else {
				IOUtils.readNBytes(stream, len) ?: return null
			}
			if (LOG.isTraceEnabled) {
				LOG.trace("readServiceProtocol result: {}", LogUtils.escape(result))
			}
			return result
		} catch (e: SocketException) {
			LOG.warn("Aborting readServiceProtocol: {}", e.toString())
		} catch (e: IOException) {
			LOG.error("Failed to read readServiceProtocol", e)
		}
		return null
	}

	internal fun setSerial(serial: String, outputStream: OutputStream, inputStream: InputStream): Boolean {
		checkSerial(serial)
		LOG.trace("setSerial({})", serial)
		var setSerialCmd = "host:tport:serial:$serial"
		setSerialCmd = String.format("%04x%s", setSerialCmd.length, setSerialCmd)
		outputStream.write(setSerialCmd.toByteArray(ADB_CHARSET))
		val ok = isOkay(inputStream, setSerialCmd)
		if (ok) {
			// 跳过 ADB server 返回的 shell-state-id，后续操作不需要它。
			inputStream.readNBytes(8)
		} else {
			LOG.error("setSerial command {} failed", LogUtils.escape(setSerialCmd))
		}
		return ok
	}

	@Throws(IOException::class)
	private fun execShellCommandRaw(cmd: String, outputStream: OutputStream, inputStream: InputStream): ByteArray? {
		var command = "shell,v2,TERM=xterm-256color,raw:$cmd"
		command = String.format("%04x%s", command.length, command)
		outputStream.write(command.toByteArray(ADB_CHARSET))
		if (isOkay(inputStream, command)) {
			return ShellProtocol.readStdout(inputStream)
		}
		return null
	}

	@Throws(IOException::class)
	internal fun execShellCommandRaw(
		serial: String,
		cmd: String,
		outputStream: OutputStream,
		inputStream: InputStream,
	): ByteArray? {
		if (setSerial(serial, outputStream, inputStream)) {
			return execShellCommandRaw(cmd, outputStream, inputStream)
		}
		return null
	}

	@Throws(IOException::class)
	fun getFeatures(): List<String> {
		val rst = exec(CMD_FEATURES)
		if (rst != null) {
			return String(rst, ADB_CHARSET).trim().split(",")
		}
		return emptyList()
	}

	@Throws(IOException::class)
	fun startServer(adbPath: String, port: Int): Boolean {
		val tcpPort = String.format("tcp:%d", port)
		val command = listOf(adbPath, "-L", tcpPort, "start-server")
		val proc = ProcessBuilder(command)
			.redirectErrorStream(true)
			.start()
		try {
			// 等待 adb server 启动；Windows 上即使很快也可能需要数秒。
			proc.waitFor(10, TimeUnit.SECONDS)
			proc.exitValue()
		} catch (e: Exception) {
			LOG.error("ADB start server failed with command: {}", command.joinToString(" "), e)
			proc.destroyForcibly()
			return false
		}
		val out = ByteArrayOutputStream()
		try {
			proc.getInputStream().use { input ->
				var read = 0
				val buf = ByteArray(1024)
				while (input.read(buf).also { read = it } >= 0) {
					out.write(buf, 0, read)
				}
			}
		} catch (e: IOException) {
			LOG.error("Failed to read adb server output", e)
		}
		return out.toString().contains(tcpPort)
	}

	fun isServerRunning(host: String, port: Int): Boolean = try {
		Socket(host, port).use {
			true
		}
	} catch (e: Exception) {
		false
	}

	/**
	 * 监听设备状态变化。
	 *
	 * @return 与 adb server 保持连接的 socket；失败时返回 null
	 */
	@Throws(IOException::class)
	fun listenForDeviceState(listener: DeviceStateListener?, host: String, port: Int): Socket? {
		val socket = connect(host, port)
		val inputStream = socket.getInputStream()
		val outputStream = socket.getOutputStream()
		if (!execCommandAsync(outputStream, inputStream, CMD_TRACK_DEVICES)) {
			socket.close()
			return null
		}
		val listenThread: ExecutorService = Executors.newFixedThreadPool(1)
		listenThread.execute {
			while (true) {
				val res = readServiceProtocol(inputStream)
				if (res == null) {
					break // socket 断开
				}
				if (listener != null) {
					val payload = String(res, ADB_CHARSET)
					val deviceLines = payload.split("\n")
					val deviceInfoList = ArrayList<ADBDeviceInfo>(deviceLines.size)
					for (deviceLine in deviceLines) {
						if (deviceLine.trim().isNotEmpty()) {
							deviceInfoList.add(ADBDeviceInfo(deviceLine, host, port))
						}
					}
					listener.onDeviceStatusChange(deviceInfoList)
				}
			}
			if (listener != null) {
				listener.adbDisconnected()
			}
		}
		return socket
	}

	@Throws(IOException::class)
	fun listForward(host: String, port: Int): List<String> {
		connect(host, port).use { socket ->
			val cmd = "0011host:list-forward"
			val inputStream = socket.getInputStream()
			val outputStream = socket.getOutputStream()
			outputStream.write(cmd.toByteArray(ADB_CHARSET))
			if (isOkay(inputStream, "listForward")) {
				val bytes = readServiceProtocol(inputStream)
				if (bytes != null) {
					return String(bytes, ADB_CHARSET).split("\n").map { it.trim() }
				}
			}
		}
		return emptyList()
	}

	@Throws(IOException::class)
	fun removeForward(host: String, port: Int, serial: String, localPort: String): Boolean {
		connect(host, port).use { socket ->
			var cmd = "host:killforward:tcp:$localPort"
			cmd = String.format("%04x%s", cmd.length, cmd)
			val inputStream = socket.getInputStream()
			val outputStream = socket.getOutputStream()
			if (setSerial(serial, outputStream, inputStream)) {
				outputStream.write(cmd.toByteArray(ADB_CHARSET))
				return isOkay(inputStream, "removeForward1") && isOkay(inputStream, "removeForward2")
			}
		}
		return false
	}

	// 小端序读取 4 字节整数
	private fun readInt(bytes: ByteArray, start: Int): Int {
		var result = bytes[start].toInt() and 0xff
		result += (bytes[start + 1].toInt() and 0xff) shl 8
		result += (bytes[start + 2].toInt() and 0xff) shl 16
		result += (bytes[start + 3].toInt() and 0xff) shl 24
		return result
	}

	private fun appendBytes(dest: ByteArray, src: ByteArray, realSrcSize: Int): ByteArray {
		val rst = ByteArray(dest.size + realSrcSize)
		System.arraycopy(dest, 0, rst, 0, dest.size)
		System.arraycopy(src, 0, rst, dest.size, realSrcSize)
		return rst
	}

	private val SERIAL_PATTERN: Pattern = Pattern.compile("^[\\w-]{8,20}$")

	private fun checkSerial(serial: String) {
		if (!SERIAL_PATTERN.matcher(serial).matches()) {
			throw IllegalArgumentException("Invalid serial: $serial")
		}
	}

	/**
	 * 把 4 个十六进制字符转换为整数。
	 */
	private fun hexToInt(hex: ByteArray): Int {
		var n = 0
		for (i in 0 until 4) {
			var b = hex[i].toInt()
			when {
				b in '0'.code..'9'.code -> b -= '0'.code
				b in 'a'.code..'f'.code -> b = b - 'a'.code + 10
				b in 'A'.code..'F'.code -> b = b - 'A'.code + 10
				else -> return -1
			}
			n = (n shl 4) or (b and 0xff)
		}
		return n
	}

	/** 监听 JDWP（可调试）进程。 */
	interface JDWPProcessListener {
		fun jdwpProcessOccurred(device: ADBDevice, id: MutableSet<String>)

		fun jdwpListenerClosed(device: ADBDevice)
	}

	/** 监听设备连接状态。 */
	interface DeviceStateListener {
		fun onDeviceStatusChange(deviceInfoList: List<ADBDeviceInfo>)

		fun adbDisconnected()
	}

	/** 设备上的一个进程（`ps` 输出的一行）。 */
	class Process {
		@JvmField
		var user: String = ""

		@JvmField
		var pid: String = ""

		@JvmField
		var ppid: String = ""

		@JvmField
		var name: String = ""

		companion object {
			/** 解析一行 `ps` 输出；字段不足时返回 null。 */
			@JvmStatic
			fun make(processLine: String): Process? {
				val fields = processLine.split(Regex("\\s+"))
				if (fields.size >= 4) {
					// 0 为 user，1 为 pid，2 为 ppid，最后一个是 name
					val proc = Process()
					proc.user = fields[0]
					proc.pid = fields[1]
					proc.ppid = fields[2]
					proc.name = fields[fields.size - 1]
					return proc
				}
				return null
			}
		}

		override fun toString(): String = StringJoiner(", ", Process::class.java.simpleName + "[", "]")
			.add("user='$user'")
			.add("pid='$pid'")
			.add("ppid='$ppid'")
			.add("name='$name'")
			.toString()
	}

	/** adb shell 协议（v2）解析。 */
	private object ShellProtocol {
		private const val ID_STD_IN = 0
		private const val ID_STD_OUT = 1
		private const val ID_STD_ERR = 2
		private const val ID_EXIT = 3

		// 尽可能关闭子进程 stdin。
		private const val ID_CLOSE_STDIN = 4

		// 窗口大小变化（struct winsize 的 ASCII 版本）。
		private const val ID_WINDOW_SIZE_CHANGE = 5

		// 非法或未知数据包。
		private const val ID_INVALID = 255

		fun readStdout(inputStream: InputStream): ByteArray? {
			val header = ByteArray(5)
			val payload = ByteArrayOutputStream()
			var tempBuf = ByteArray(1024)
			var exit = false
			while (!exit) {
				IOUtils.read(inputStream, header)
				exit = header[0].toInt() == ID_EXIT
				val payloadSize = readInt(header, 1)
				if (tempBuf.size < payloadSize) {
					tempBuf = ByteArray(payloadSize)
				}
				val readSize = IOUtils.read(inputStream, tempBuf, 0, payloadSize)
				if (readSize != payloadSize) {
					LOG.error("Failed to read ShellProtocol data")
					return null // 不想返回损坏的数据。
				}
				payload.write(tempBuf, 0, readSize)
			}
			return payload.toByteArray()
		}
	}
}
