package jadx.gui.settings.ui.font

import jadx.gui.settings.JadxSettings
import jadx.gui.utils.FontUtils
import jadx.gui.utils.NLS
import org.drjekyll.fontchooser.FontChooser
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.WindowConstants

/**
 * 字体选择对话框：包装第三方 [FontChooser]，并接入 jadx 的窗口位置记忆与 NLS。
 */
class JadxFontDialog(
	parent: Dialog,
	private val settings: JadxSettings,
	title: String,
) : JDialog(parent, title, true) {

	private val fontChooser: FontChooser = FontChooser()
	private var selected: Boolean = false

	init {
		initComponents()
		defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
		if (!settings.loadWindowPos(this)) {
			pack()
		}
	}

	/**
	 * 打开对话框选择字体；用户确认且字体发生变化时返回复合字体，否则返回 null。
	 */
	fun select(currentFont: Font, onlyMonospace: Boolean): Font? {
		fontChooser.setSelectedFont(currentFont)
		if (onlyMonospace) {
			FontChooserHack.setOnlyMonospace(fontChooser)
		}
		isVisible = true
		val selectedFont = fontChooser.selectedFont
		if (selected && selectedFont != currentFont) {
			return FontUtils.getCompositeFont(selectedFont.family, selectedFont.style, selectedFont.size)
		}
		return null
	}

	private fun initComponents() {
		val chooserPanel = JPanel()
		chooserPanel.border = BorderFactory.createEmptyBorder(10, 10, 0, 10)
		chooserPanel.layout = BorderLayout(0, 10)
		chooserPanel.add(fontChooser)

		val controlPanel = JPanel()
		controlPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
		controlPanel.layout = FlowLayout(FlowLayout.TRAILING)

		val okBtn = JButton()
		okBtn.text = NLS.str("common_dialog.ok")
		okBtn.mnemonic = 'o'.code
		okBtn.addActionListener {
			selected = true
			dispose()
		}

		val cancelBtn = JButton()
		cancelBtn.text = NLS.str("common_dialog.cancel")
		cancelBtn.mnemonic = 'c'.code
		cancelBtn.addActionListener { dispose() }

		controlPanel.add(okBtn)
		controlPanel.add(cancelBtn)

		add(chooserPanel)
		add(controlPanel, BorderLayout.PAGE_END)
		rootPane.defaultButton = okBtn
	}

	override fun dispose() {
		settings.saveWindowPos(this)
		super.dispose()
	}

	companion object {
		private const val serialVersionUID: Long = 7609857698785777587L
	}
}
