package kadx.gui.settings

import java.awt.Rectangle
import java.util.Objects

/**
 * 窗口位置与尺寸的持久化记录。
 *
 * **做什么**：`KadxSettings.saveWindowPos` 把每个窗口的 [Rectangle] 边界按窗口类名存入配置，
 * 下次启动时 `loadWindowPos` 再恢复。
 *
 * **为什么保留无参构造器与显式 getter/setter**：本类由 Gson 直接读写（无参构造 + 字段名
 * `windowId`/`bounds` 即 JSON 键），同时原 Java 自定义了 `equals`/`hashCode`，需原样保留。
 */
@Suppress("unused")
class WindowLocation {

	private var windowId: String = ""
	private var bounds: Rectangle? = null

	// 不要删除：Gson 反序列化需要无参构造器
	constructor()

	constructor(windowId: String, bounds: Rectangle?) {
		this.windowId = windowId
		this.bounds = bounds
	}

	fun getWindowId(): String = windowId

	fun setWindowId(windowId: String) {
		this.windowId = windowId
	}

	fun getBounds(): Rectangle? = bounds

	fun setBounds(bounds: Rectangle?) {
		this.bounds = bounds
	}

	override fun hashCode(): Int = Objects.hashCode(windowId)

	override fun equals(other: Any?): Boolean {
		if (other is WindowLocation) {
			return Objects.equals(windowId, other.windowId) &&
				Objects.equals(bounds, other.bounds)
		}
		return false
	}

	override fun toString(): String = "WindowLocation{id=$windowId, bounds=$bounds}"
}
