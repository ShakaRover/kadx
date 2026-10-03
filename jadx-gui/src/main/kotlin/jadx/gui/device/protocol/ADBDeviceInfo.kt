package jadx.gui.device.protocol

import jadx.core.utils.log.LogUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.TreeMap

/**
 * 单个 ADB 设备的元信息。
 *
 * **做什么**：解析 `adb devices -l` 输出中的一行，例如
 * `emulator-5554 device product:sdk model:sdk_gphone transport_id:1`，
 * 提取序列号、状态、型号以及全部 `key:value` 属性。
 *
 * **为什么用 [TreeMap]**：属性输出顺序稳定，便于调试与展示。
 */
class ADBDeviceInfo internal constructor(info: String, host: String, port: Int) {
	/** 设备序列号。 */
	private val serial: String

	/** 设备状态，如 `device`、`offline`。 */
	private val state: String

	/** 设备型号，缺省时回退为序列号。 */
	private val model: String

	/** 完整的原始信息行。 */
	private val allInfo: String

	/** adb server 主机。 */
	private val adbHost: String

	/** adb server 端口。 */
	private val adbPort: Int

	/**
	 * 保存 `device`、`model`、`product`、`transport_id` 等属性值。
	 */
	private val propertiesMap: MutableMap<String, String> = TreeMap()

	init {
		val infoFields = info.trim().split(Regex("\\s+"))
		allInfo = infoFields.joinToString(" ")
		if (infoFields.size > 2) {
			serial = infoFields[0]
			state = infoFields[1]

			for (i in 2 until infoFields.size) {
				val field = infoFields[i]
				val idx = field.indexOf(':')
				if (idx > 0) {
					val key = field.substring(0, idx)
					val value = field.substring(idx + 1)
					if (value.isNotEmpty()) {
						propertiesMap[key] = value
					}
				}
			}
			model = propertiesMap.getOrDefault("model", serial)
		} else {
			LOG.error("Unable to extract device information from {}", LogUtils.escape(info))
			serial = ""
			state = "unknown"
			model = "unknown"
		}
		adbHost = host
		adbPort = port
	}

	/** @return 设备是否处于在线（`device`）状态 */
	fun isOnline(): Boolean = state == "device"

	/** @return adb server 主机 */
	fun getAdbHost(): String = adbHost

	/** @return adb server 端口 */
	fun getAdbPort(): Int = adbPort

	/** @return 设备序列号 */
	fun getSerial(): String = serial

	/** @return 设备状态 */
	fun getState(): String = state

	/** @return 设备型号 */
	fun getModel(): String = model

	/** @return 完整的原始信息行 */
	fun getAllInfo(): String = allInfo

	/** 按 key 读取属性值，不存在时返回 null。 */
	fun getProperty(key: String): String? = propertiesMap[key]

	override fun toString(): String = allInfo

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ADBDeviceInfo::class.java)
	}
}
