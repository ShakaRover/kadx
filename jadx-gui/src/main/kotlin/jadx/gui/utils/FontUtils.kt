package jadx.gui.utils

import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Font
import java.io.InputStream

/**
 * 字体工具。
 *
 * **做什么**：在「`家族/样式/字号`」字符串与 [Font] 对象之间互相转换，
 * 加载内置 TTF 字体，并判断某字体能否显示指定字符串。
 *
 * **为什么用 `object`**：原 Java 全部是静态方法，`object` + `@JvmStatic`
 * 保持 Java 侧 `FontUtils.xxx(...)` 调用不变。
 */
object FontUtils {

	private val LOG: Logger = LoggerFactory.getLogger(FontUtils::class.java)

	/** 解析 `家族/样式/字号` 描述串（例如 `JetBrains Mono/bold italic/13`）为 [Font]。 */
	@JvmStatic
	fun loadByStr(fontDesc: String): Font {
		val parts = fontDesc.split("/")
		if (parts.size != 3) {
			throw JadxRuntimeException("Unsupported font description format: $fontDesc")
		}
		val family = parts[0]
		val style = parseFontStyle(parts[1])
		val size = Integer.parseInt(parts[2])

		val font = getCompositeFont(family, style, size)
		if (font == null) {
			throw JadxRuntimeException("Font not found: $fontDesc")
		}
		return font
	}

	/** 把 [Font] 序列化为 `家族/样式/字号` 描述串；null 返回空串。 */
	@JvmStatic
	fun convertToStr(font: Font?): String {
		if (font == null) {
			return ""
		}
		if (font.size < 1) {
			throw JadxRuntimeException("Bad font size: " + font.size)
		}
		return font.family +
			'/' + convertFontStyleToString(font.style) +
			'/' + font.size
	}

	/** 把 [Font] 的样式位掩码转换为可读字符串（plain/bold/italic）。 */
	@JvmStatic
	fun convertFontStyleToString(style: Int): String {
		if (style == 0) {
			return "plain"
		}
		val sb = StringBuilder()
		// Font.BOLD / Font.ITALIC 是位标志，需要按位与判断
		if ((style and Font.BOLD) != 0) {
			sb.append("bold")
		}
		if ((style and Font.ITALIC) != 0) {
			sb.append(" italic")
		}
		return sb.toString().trim()
	}

	private fun parseFontStyle(str: String): Int {
		var style = 0
		if (str.contains("bold")) {
			style = style or Font.BOLD
		}
		if (str.contains("italic")) {
			style = style or Font.ITALIC
		}
		return style
	}

	/** 从 `resources/fonts/<name>.ttf` 加载内置字体；失败返回 null。 */
	@JvmStatic
	fun openFontTTF(name: String): Font? {
		val fontPath = "/fonts/$name.ttf"
		return try {
			UiUtils::class.java.getResourceAsStream(fontPath).use { stream ->
				Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(12f)
			}
		} catch (e: Exception) {
			LOG.error("Failed load font by path: {}", fontPath, e)
			null
		}
	}

	/** 判断 [font] 是否能显示 [str] 中的全部码点。 */
	@JvmStatic
	fun canStringBeDisplayed(str: String?, font: Font): Boolean {
		if (str == null || str.isEmpty()) {
			return true
		}
		var offset = 0
		while (offset < str.length) {
			val codePoint = str.codePointAt(offset)
			if (!font.canDisplay(codePoint)) {
				return false
			}
			offset += Character.charCount(codePoint)
		}
		return true
	}

	/**
	 * 获取「复合字体」。
	 *
	 * 见 https://github.com/JFormDesigner/FlatLaf/issues/923
	 * 切换字体时应使用 `font.deriveFont()` 或本方法，而不是 `new Font(...)`，
	 * 否则会丢失 FlatLaf 的 CJK 字体支持。
	 */
	@JvmStatic
	fun getCompositeFont(family: String, style: Int, size: Int): Font = com.formdev.flatlaf.util.FontUtils.getCompositeFont(family, style, size)

	/** 把任意字体转换为对应的复合字体。 */
	@JvmStatic
	fun toCompositeFont(font: Font): Font = getCompositeFont(font.family, font.style, font.size)
}
