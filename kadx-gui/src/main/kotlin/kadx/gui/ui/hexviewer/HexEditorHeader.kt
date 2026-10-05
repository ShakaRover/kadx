package kadx.gui.ui.hexviewer

import org.exbin.bined.CodeAreaCaretListener
import org.exbin.bined.CodeAreaSection
import org.exbin.bined.DataChangedListener
import org.exbin.bined.SelectionChangedListener
import org.exbin.bined.SelectionRange
import org.exbin.bined.basic.BasicCodeAreaSection
import org.exbin.bined.swing.section.SectCodeArea
import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.UIManager

/**
 * 十六进制视图底部的状态栏（页眉）。
 *
 * **做什么**：自绘一行文本，显示当前选区范围（Sel）、选中长度/总长度（Len）、
 * 当前区段是十六进制还是文本（HEX/TXT）以及字符集名称。
 *
 * **为什么手动绘制**：为了与十六进制编辑器的等宽字体、颜色完全对齐，
 * 这里用 [paintComponent] 逐段测量并绘制，而不是用多个 JLabel。
 */
class HexEditorHeader(private val parent: SectCodeArea) : JComponent() {

	private val dataChangedListener = DataChangedListener { repaint() }
	private val caretMovedListener = CodeAreaCaretListener { repaint() }
	private val selectionChangedListener = SelectionChangedListener { repaint() }

	private var minimumSize: Dimension? = null
	private var preferredSize: Dimension? = null

	init {
		parent.addCaretMovedListener(caretMovedListener)
		parent.addSelectionChangedListener(selectionChangedListener)
		parent.addDataChangedListener(dataChangedListener)
	}

	override fun addNotify() {
		super.addNotify()
		parent.addCaretMovedListener(caretMovedListener)
		parent.addSelectionChangedListener(selectionChangedListener)
		parent.addDataChangedListener(dataChangedListener)
	}

	override fun removeNotify() {
		parent.removeCaretMovedListener(caretMovedListener)
		parent.removeSelectionChangedListener(selectionChangedListener)
		parent.removeDataChangedListener(dataChangedListener)
		super.removeNotify()
	}

	override fun getMinimumSize(): Dimension {
		minimumSize?.let { return it }
		val i = getInsets()
		val fm = getFontMetrics(parent.getFont())
		if (fm == null) {
			return Dimension(100, 20) // 兜底
		}
		val ch = fm.getHeight() + 2 // 行高
		val cw = fm.stringWidth(HEX_ALPHABET) / 16 // 字符宽度估算

		val sampleText = "Sel: 00000000:00000000 Len: 00000000/00000000 TXT UTF-8"
		val minTextWidth = fm.stringWidth(sampleText)
		val minimumWidth = minTextWidth + cw * 5 + i.left + i.right

		val minimumHeight = ch + 5 + i.top + i.bottom

		return Dimension(minimumWidth, minimumHeight)
	}

	override fun setMinimumSize(minimumSize: Dimension) {
		this.minimumSize = minimumSize
		revalidate()
	}

	override fun getPreferredSize(): Dimension {
		preferredSize?.let { return it }
		val i = getInsets()
		val fm = getFontMetrics(parent.getFont())
		if (fm == null) {
			return getMinimumSize() // 兜底
		}
		val ch = fm.getHeight() + 2
		val cw = fm.stringWidth(HEX_ALPHABET) / 16

		val sampleText = "Sel: 00000000:00000000 Len: 00000000/00000000 TXT UTF-8"
		val preferredTextWidth = fm.stringWidth(sampleText)
		val preferredWidth = preferredTextWidth + cw * 10 + i.left + i.right
		val preferredHeight = ch + 5 + i.top + i.bottom

		return Dimension(preferredWidth, preferredHeight)
	}

	override fun setPreferredSize(preferredSize: Dimension) {
		this.preferredSize = preferredSize
		revalidate()
	}

	override fun paintComponent(g: Graphics) {
		// 标准 Graphics2D 初始化
		if (g is Graphics2D) {
			g.setRenderingHint(
				RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
			)
		}

		// 取内边距、宽高
		val i = getInsets()
		val fw = getWidth()
		val fh = getHeight()
		val w = fw - i.left - i.right
		val h = fh - i.top - i.bottom

		// 取字体度量
		g.setFont(parent.getFont())
		val fm = g.getFontMetrics()
		if (fm == null) {
			return // 没有字体度量就无法绘制
		}
		val ca = fm.getAscent() + 1 // 基线
		val ch = fm.getHeight() + 2 // 行高（含内边距）
		var cw = fm.stringWidth(HEX_ALPHABET) / 16 // 字符宽度估算
		if (cw <= 0) {
			cw = 1 // 避免除零或错误计算
		}

		// 从父编辑器取颜色与状态
		val separatorForeground = UIManager.getColor("Separator.foreground")
		val themeBackground = UIManager.getColor("Panel.background")
		val themeForeground = UIManager.getColor("Panel.foreground")

		val selectionRange = parent.getSelection()
		val ss = selectionRange.getStart()
		val se = selectionRange.getEnd()
		val sl = selectionRange.getLength()
		val length = parent.getDataSize()

		// 文字基线（垂直居中）
		val ty = i.top + ((h - ch) / 2) + ca

		// 画背景
		g.setColor(themeBackground)
		g.fillRect(i.left, i.top, w, h)

		// 画文字（Sel、Len、状态）
		g.setColor(themeForeground)

		var currentX = i.left + cw / 2 // 左侧留一点内边距

		// 选区范围（Sel: start:end）
		val selLabel = "Sel:"
		g.drawString(selLabel, currentX, ty)
		currentX += fm.stringWidth(selLabel) + cw

		val sss = addressString(ss)
		g.drawString(sss, currentX, ty)
		currentX += fm.stringWidth(sss)

		val separator1 = ":"
		g.drawString(separator1, currentX, ty)
		currentX += fm.stringWidth(separator1)

		val ses = addressString(se)
		g.drawString(ses, currentX, ty)
		currentX += fm.stringWidth(ses) + cw

		// Sel 之后的竖直分隔线
		val dividerTopY = i.top
		val dividerHeight = h
		g.setColor(separatorForeground)
		g.fillRect(currentX, dividerTopY, 1, dividerHeight)
		currentX += cw

		// 长度信息（Len: selected/total）
		g.setColor(themeForeground)
		val lenLabel = "Len:"
		g.drawString(lenLabel, currentX, ty)
		currentX += fm.stringWidth(lenLabel) + cw

		val sls = addressString(sl)
		g.drawString(sls, currentX, ty)
		currentX += fm.stringWidth(sls)

		val separator2 = "/"
		g.drawString(separator2, currentX, ty)
		currentX += fm.stringWidth(separator2)

		val ls = addressString(length)
		g.drawString(ls, currentX, ty)
		currentX += fm.stringWidth(ls) + cw

		// Len 之后的竖直分隔线
		g.setColor(separatorForeground)
		g.fillRect(currentX, dividerTopY, 1, dividerHeight)
		currentX += cw

		// 状态（TXT/HEX、字符集）
		g.setColor(themeForeground)

		var statusTxtHex = "HEX"
		val section: CodeAreaSection = parent.getActiveSection()
		if (section == BasicCodeAreaSection.TEXT_PREVIEW) {
			statusTxtHex = "TXT"
		}

		g.drawString(statusTxtHex, currentX, ty)
		currentX += fm.stringWidth(statusTxtHex) + cw

		// TXT/HEX 之后的竖直分隔线
		g.setColor(separatorForeground)
		g.fillRect(currentX, dividerTopY, 1, dividerHeight)
		currentX += cw

		g.setColor(themeForeground)

		// 字符集
		val statusCharset = parent.getCharset().name()
		g.drawString(statusCharset, currentX, ty)

		// 底部边框
		g.setColor(separatorForeground)
		g.fillRect(i.left, i.top + h - 1, w, 1)
	}

	fun addressString(address: Long): String = String.format("%08X", address)

	companion object {
		private const val serialVersionUID = 1L
		private const val HEX_ALPHABET = "0123456789ABCDEF"
	}
}
