package jadx.gui.ui.dialog

import jadx.gui.utils.NLS
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Component
import java.nio.charset.Charset
import javax.swing.JOptionPane

/**
 * 字符集选择对话框（基于 [JOptionPane] 的输入框）。
 *
 * **做什么**：把系统可用字符集按显示名排序后展示，返回用户所选字符集的规范名；
 * 用户取消时返回 `null`。
 *
 * **为什么用 `object`**：原 Java 只有静态方法，`object` + `@JvmStatic` 可让
 * Java 调用方 `CharsetDialog.chooseCharset(...)` 零改动。
 */
object CharsetDialog {
	private val LOG: Logger = LoggerFactory.getLogger(CharsetDialog::class.java)

	private val CHARSET_COMPARATOR: Comparator<Charset> = Comparator.comparing(
		{ charset: Charset -> charset.displayName() },
		String.CASE_INSENSITIVE_ORDER,
	)

	/**
	 * 弹出字符集选择框。
	 *
	 * @param parent 父组件（可为 `null`）
	 * @param currentCharsetName 当前字符集名，用于预选；不可用时忽略
	 * @return 选中字符集的规范名；取消返回 `null`
	 */
	fun chooseCharset(parent: Component?, currentCharsetName: String?): String? {
		val availableCharsets: Collection<Charset> = Charset.availableCharsets().values

		val sortedCharsets: List<Charset> = availableCharsets.sortedWith(CHARSET_COMPARATOR)

		var initialSelection: Charset? = null
		try {
			if (currentCharsetName != null && Charset.isSupported(currentCharsetName)) {
				initialSelection = Charset.forName(currentCharsetName)
				if (!sortedCharsets.contains(initialSelection)) {
					initialSelection = null
				}
			}
		} catch (e: Exception) {
			LOG.warn("Failed to find initial charset '{}'", currentCharsetName, e)
		}
		if (initialSelection == null && sortedCharsets.isNotEmpty()) {
			initialSelection = sortedCharsets[0]
		}
		val charsetArray = sortedCharsets.toTypedArray()

		val selectedValue = JOptionPane.showInputDialog(
			parent,
			NLS.str("encoding_dialog.message"),
			NLS.str("encoding_dialog.title"),
			JOptionPane.INFORMATION_MESSAGE,
			null,
			charsetArray,
			initialSelection,
		)

		return (selectedValue as? Charset)?.name()
	}
}
