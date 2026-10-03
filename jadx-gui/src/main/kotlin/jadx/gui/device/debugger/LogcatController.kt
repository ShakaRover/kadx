package jadx.gui.device.debugger

import jadx.gui.device.protocol.ADBDevice
import jadx.gui.ui.panel.LogcatPanel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Timer
import java.util.TimerTask
import java.util.TreeSet

/**
 * 设备 logcat 的读取与过滤控制器。
 *
 * **做什么**：定时（每 1 秒）从设备拉取二进制 logcat，解析出 [LogcatInfo]，
 * 按 [LogcatFilter] 过滤后推给 [LogcatPanel] 展示。
 *
 * **线程模型**：沿用原 Java 的 [Timer] + [TimerTask] 后台线程，不引入协程。
 */
class LogcatController
@Throws(IOException::class)
constructor(
	private val logcatPanel: LogcatPanel,
	private val adbDevice: ADBDevice,
) {

	private var timer: Timer = Timer()

	/** 设备时区，用于格式化时间戳。 */
	private val timezone: String

	/** 最近一条日志（用于增量拉取）。 */
	private var recent: LogcatInfo? = null

	/** 已接收的全部日志事件。 */
	private var events: MutableList<LogcatInfo> = ArrayList()

	/** 当前过滤器。 */
	private var filter: LogcatFilter = LogcatFilter()

	private var status: String = "null"

	init {
		this.timezone = adbDevice.timezone
		this.startLogcat()
	}

	/** 启动定时拉取。 */
	fun startLogcat() {
		timer = Timer()
		timer.schedule(
			object : TimerTask() {
				override fun run() {
					getLog()
				}
			},
			0,
			1000,
		)
		this.status = "running"
	}

	/** 停止定时拉取。 */
	fun stopLogcat() {
		timer.cancel()
		this.status = "stopped"
	}

	fun getStatus(): String = this.status

	/** 清空设备与本地缓存的 logcat。 */
	fun clearLogcat() {
		try {
			adbDevice.clearLogcat()
			clearEvents()
		} catch (e: IOException) {
			LOG.error("Failed to clear Logcat", e)
		}
	}

	private fun getLog() {
		if (!logcatPanel.isReady) {
			return
		}
		try {
			val buf: ByteArray?
			if (recent == null) {
				buf = adbDevice.binaryLogcat
			} else {
				buf = adbDevice.getBinaryLogcat(checkNotNull(recent).afterTimestamp)
			}
			if (buf == null) {
				return
			}
			val input = ByteBuffer.wrap(buf)
			input.order(ByteOrder.LITTLE_ENDIAN)
			while (input.remaining() > 20) {
				var eInfo: LogcatInfo? = null
				val msgBuf: ByteArray
				val eLen = input.getShort()
				val eHdrLen = input.getShort()
				if (eLen + eHdrLen > input.remaining()) {
					return
				}
				when (eHdrLen.toInt()) {
					20 -> { // header length 20 == version 1
						eInfo = LogcatInfo(eLen, eHdrLen, input.getInt(), input.getInt(), input.getInt(), input.getInt(), input.get())
						msgBuf = ByteArray(eLen.toInt())
						input.get(msgBuf, 0, eLen - 1)
						eInfo.setMsg(msgBuf)
					}

					24 -> { // header length 24 == version 2 / 3
						eInfo = LogcatInfo(eLen, eHdrLen, input.getInt(), input.getInt(), input.getInt(), input.getInt(), input.getInt(), input.get())
						msgBuf = ByteArray(eLen.toInt())
						input.get(msgBuf, 0, eLen - 1)
						eInfo.setMsg(msgBuf)
					}

					28 -> { // header length 28 == version 4
						eInfo = LogcatInfo(
							eLen, eHdrLen, input.getInt(), input.getInt(), input.getInt(), input.getInt(),
							input.getInt(), input.getInt(), input.get(),
						)
						msgBuf = ByteArray(eLen.toInt())
						input.get(msgBuf, 0, eLen - 1)
						eInfo.setMsg(msgBuf)
					}

					else -> {
					}
				}
				val info = eInfo ?: return
				if (recent == null) {
					recent = info
				} else if (checkNotNull(recent).instant.isBefore(info.instant)) {
					recent = info
				}

				if (filter.doFilter(info)) {
					logcatPanel.log(info)
				}
				events.add(info)
			}
		} catch (e: Exception) {
			LOG.error("Failed to get logcat message", e)
		}
	}

	fun reload(): Boolean {
		stopLogcat()
		val ok = logcatPanel.clearLogcatArea()
		if (ok) {
			events.forEach { eInfo ->
				if (filter.doFilter(eInfo)) {
					logcatPanel.log(eInfo)
				}
			}
			startLogcat()
		}
		return true
	}

	fun clearEvents() {
		this.recent = null
		this.events = ArrayList()
	}

	fun exit() {
		stopLogcat()
		filter = LogcatFilter()
		recent = null
	}

	fun getFilter(): LogcatFilter = this.filter

	/** logcat 过滤器：按 pid 与消息级别过滤。 */
	inner class LogcatFilter {
		private val pid: MutableSet<Int>
		private val msgType: MutableSet<Byte>

		constructor() : this(
			TreeSet(),
			TreeSet(listOf<Byte>(1, 2, 3, 4, 5, 6, 7, 8)),
		)

		constructor(pid: MutableSet<Int>, msgType: MutableSet<Byte>) {
			this.pid = pid
			this.msgType = msgType
		}

		fun addPid(pid: Int) {
			this.pid.add(pid)
		}

		fun removePid(pid: Int) {
			this.pid.remove(pid)
		}

		fun togglePid(pid: Int, state: Boolean) {
			if (state) {
				addPid(pid)
			} else {
				removePid(pid)
			}
		}

		fun addMsgType(msgType: Byte) {
			this.msgType.add(msgType)
		}

		fun removeMsgType(msgType: Byte) {
			this.msgType.remove(msgType)
		}

		fun toggleMsgType(msgType: Byte, state: Boolean) {
			if (state) {
				addMsgType(msgType)
			} else {
				removeMsgType(msgType)
			}
		}

		fun doFilter(inInfo: LogcatInfo): Boolean = pid.contains(inInfo.getPid()) && msgType.contains(inInfo.getMsgType())

		fun getFilteredList(inInfoList: List<LogcatInfo>): List<LogcatInfo> {
			val outInfoList = ArrayList<LogcatInfo>()
			inInfoList.forEach { inInfo ->
				if (doFilter(inInfo)) {
					outInfoList.add(inInfo)
				}
			}
			return outInfoList
		}
	}

	/** 一条解析后的 logcat 记录。 */
	inner class LogcatInfo {
		private var msg: String? = null
		private val msgType: Byte
		private val nsec: Int
		private val pid: Int
		private val sec: Int
		private val tid: Int
		private val hdrSize: Short
		private val len: Short
		private val version: Short
		private var lid: Int = 0
		private var uid: Int = 0

		constructor(len: Short, hdrSize: Short, pid: Int, tid: Int, sec: Int, nsec: Int, msgType: Byte) {
			this.hdrSize = hdrSize
			this.len = len
			this.msgType = msgType
			this.nsec = nsec
			this.pid = pid
			this.sec = sec
			this.tid = tid
			this.version = 1
		}

		// 版本 2 与 3 参数相同
		constructor(len: Short, hdrSize: Short, pid: Int, tid: Int, sec: Int, nsec: Int, lid: Int, msgType: Byte) {
			this.hdrSize = hdrSize
			this.len = len
			this.lid = lid
			this.msgType = msgType
			this.nsec = nsec
			this.pid = pid
			this.sec = sec
			this.tid = tid
			this.version = 3
		}

		constructor(len: Short, hdrSize: Short, pid: Int, tid: Int, sec: Int, nsec: Int, lid: Int, uid: Int, msgType: Byte) {
			this.hdrSize = hdrSize
			this.len = len
			this.lid = lid
			this.msgType = msgType
			this.nsec = nsec
			this.pid = pid
			this.sec = sec
			this.tid = tid
			this.uid = uid
			this.version = 4
		}

		fun setMsg(msg: ByteArray) {
			this.msg = String(msg)
		}

		fun getVersion(): Short = this.version

		fun getLen(): Short = this.len

		val headerLen: Short get() = this.hdrSize

		fun getPid(): Int = this.pid

		fun getTid(): Int = this.tid

		fun getSec(): Int = this.sec

		val nSec: Int get() = this.nsec

		fun getLid(): Int = this.lid

		fun getUid(): Int = this.uid

		val instant: Instant get() = Instant.ofEpochSecond(getSec().toLong(), nSec.toLong())

		val timestamp: String get() {
			val dtFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.of(timezone))
			return dtFormat.format(instant)
		}

		val afterTimestamp: String get() {
			val dtFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.of(timezone))
			return dtFormat.format(instant.plusMillis(1))
		}

		fun getMsgType(): Byte = this.msgType

		val msgTypeString: String get() = when (getMsgType().toInt()) {
			0 -> "Unknown"
			1 -> "Default"
			2 -> "Verbose"
			3 -> "Debug"
			4 -> "Info"
			5 -> "Warn"
			6 -> "Error"
			7 -> "Fatal"
			8 -> "Silent"
			else -> "Unknown"
		}

		fun getMsg(): String? = this.msg
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(LogcatController::class.java)
	}
}
