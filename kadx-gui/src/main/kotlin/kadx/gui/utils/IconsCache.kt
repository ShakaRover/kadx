package kadx.gui.utils

import java.util.concurrent.ConcurrentHashMap
import javax.swing.ImageIcon

/**
 * SVG 图标的缓存表。
 *
 * **做什么**：把 SVG 图标名映射到已经解析好的 [ImageIcon]，避免重复解析 SVG（解析代价较高）。
 *
 * **为什么用 `object`**：原 Java 类只有静态方法，`object` + `@JvmStatic` 能保持
 * Java 侧 `IconsCache.getSVGIcon(...)` 的静态调用方式不变。
 */
object IconsCache {

	/** 图标名 -> 图标实例。使用并发 Map，允许后台线程与 UI 线程同时取图标。 */
	private val SVG_ICONS = ConcurrentHashMap<String, ImageIcon>()

	/**
	 * 获取（并缓存）指定名称的 SVG 图标。
	 *
	 * @param name 图标资源名（不含 `icons/` 前缀与 `.svg` 后缀）
	 */
	fun getSVGIcon(name: String): ImageIcon = SVG_ICONS.computeIfAbsent(name) { UiUtils.openSvgIcon(it) }
}
