package kadx.gui.settings.font

import kadx.gui.utils.FontUtils
import kadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Font

/**
 * 字体更新的通用适配器：负责字体对象的加载、切换以及与设置数据的同步。
 *
 * **做什么**：内部保存「默认字体」「当前字体」「按 UI 缩放后的有效字体」，
 * 并在字体变化时把序列化字符串回写到设置对象（通过 [fontSetter]）。
 *
 * **为什么这样设计**：设置数据里的字体是以 `家族/样式/字号` 字符串持久化的，
 * 而运行时需要真正的 [Font] 对象；本类充当两者之间的桥梁。
 */
class FontAdapter(defaultFont: Font) {

	private var defaultFont: Font
	private var font: Font
	private var effectiveFont: Font

	/** 字体变化时把序列化字符串写回设置；在 [bindData] 之前调用 setFont 会抛未初始化异常。 */
	private lateinit var fontSetter: (String) -> Unit
	private var uiZoom: Float = 0f
	private var desktopScale: Float = 1.0f

	init {
		// Kotlin 非空参数已经隐含了 Objects.requireNonNull 的检查语义
		this.defaultFont = defaultFont
		this.font = defaultFont
		this.effectiveFont = defaultFont
	}

	/**
	 * 从设置数据加载当前字体，并保存 setter 以便后续同步。
	 */
	fun bindData(fontStr: String?, fontStrSetter: (String) -> Unit) {
		font = loadFromStr(fontStr)
		fontSetter = fontStrSetter
	}

	/**
	 * 更新默认字体；若当前字体就是旧默认值，则一并切换到新默认值。
	 */
	fun setDefaultFont(newDefaultFont: Font) {
		val newDefFont = FontUtils.toCompositeFont(newDefaultFont)
		val prevDefaultFont = defaultFont
		defaultFont = newDefFont
		if (font === prevDefaultFont) {
			// 当前字体仍是默认字体 => 跟随新的默认字体一起更新
			setFont(newDefFont)
		}
	}

	fun getFont(): Font = font

	fun getEffectiveFont(): Font = effectiveFont

	/**
	 * 设置当前字体；传入 null 表示恢复默认字体。
	 */
	fun setFont(newFont: Font?) {
		if (newFont != null) {
			font = FontUtils.toCompositeFont(newFont)
		} else {
			font = defaultFont
		}
		fontSetter(fontStr)
		applyFontZoom()
	}

	fun setUiZoom(uiZoom: Float) {
		this.uiZoom = uiZoom
		applyFontZoom()
	}

	/** 设置桌面缩放系数（Linux HiDPI），与用户缩放相乘。 */
	fun setDesktopScale(scale: Float) {
		desktopScale = scale
		applyFontZoom()
	}

	private fun loadFromStr(fontStr: String?): Font {
		if (fontStr != null && fontStr.isNotEmpty()) {
			try {
				return FontUtils.loadByStr(fontStr)
			} catch (e: Exception) {
				LOG.warn("Failed to load font: {}, reset to default", fontStr, e)
			}
		}
		return defaultFont
	}

	private val fontStr: String get() {
		if (font === defaultFont) {
			return ""
		}
		return FontUtils.convertToStr(font)
	}

	private fun applyFontZoom() {
		val zoom = uiZoom * desktopScale
		if (UiUtils.nearlyEqual(zoom, 1.0f)) {
			effectiveFont = font
		} else {
			effectiveFont = font.deriveFont((font.size2D * zoom).toFloat())
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(FontAdapter::class.java)
	}
}
