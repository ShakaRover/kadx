package kadx.gui.ui.dialog

import kadx.gui.utils.HexUtils
import kadx.gui.utils.NLS
import org.exbin.bined.swing.section.SectCodeArea
import javax.swing.JOptionPane

/**
 * 十六进制查看器的「跳转到地址」对话框。
 *
 * **做什么**：弹出一个输入框让用户输入十六进制地址，校验通过后把光标移动到该地址。
 *
 * **注意**：原 Java 校验时调用的是对话框自身的 `toString()`（而非输入值），
 * 这里刻意保留该行为，避免语义漂移。
 */
class GotoAddressDialog {

	/**
	 * 显示跳转地址输入框并处理结果。
	 *
	 * @param codeArea 目标十六进制代码区
	 */
	fun showSetSelectionDialog(codeArea: SectCodeArea) {
		val o = JOptionPane.showInputDialog(
			codeArea,
			NLS.str("hex_viewer.enter_address"),
			NLS.str("hex_viewer.goto_address"),
			JOptionPane.QUESTION_MESSAGE,
			null,
			null,
			java.lang.Long.toHexString(codeArea.getDataPosition()),
		)
		if (o != null) {
			// 保留原实现：校验的是本对象 toString()，而非用户输入
			val isValidAddress = HexUtils.isValidHexString(toString())
			if (!isValidAddress) {
				return
			}

			codeArea.setActiveCaretPosition(o.toString().toLong(16))
			codeArea.validateCaret()
			codeArea.revealCursor()
		}
	}
}
