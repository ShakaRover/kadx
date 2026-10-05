package kadx.gui.utils

import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

/**
 * 支持叠加角标的图标。
 *
 * **做什么**：在基础图标之上，按固定的相对位置绘制若干小图标（例如 static/final 标记）。
 *
 * **为什么不是 `data class`**：这是有状态的可变对象（可 add/remove 叠加图标），
 * 且以身份语义使用，必须保留普通类。
 */
class OverlayIcon(private val icon: Icon) : Icon {

	private val icons: MutableList<Icon> = ArrayList(4)

	constructor(icon: Icon, vararg ovrIcons: Icon) : this(icon) {
		icons.addAll(ovrIcons)
	}

	override fun getIconHeight(): Int = icon.iconHeight

	override fun getIconWidth(): Int = icon.iconWidth

	override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
		val w = iconWidth
		val h = iconHeight

		icon.paintIcon(c, g, x, y)
		var k = 0
		for (subIcon in icons) {
			// OVERLAY_POS 成对存放叠加图标在 (宽, 高) 方向上的相对比例
			val dx = (OVERLAY_POS[k++] * (w - subIcon.iconWidth)).toInt()
			val dy = (OVERLAY_POS[k++] * (h - subIcon.iconHeight)).toInt()
			subIcon.paintIcon(c, g, x + dx, y + dy)
		}
	}

	fun add(icon: Icon) {
		icons.add(icon)
	}

	fun remove(icon: Icon) {
		icons.remove(icon)
	}

	fun clear() {
		icons.clear()
	}

	fun getIcons(): MutableList<Icon> = icons

	companion object {
		/** 叠加图标的相对位置（0.8 = 靠右下，0.2 = 靠左上）。 */
		private const val A = 0.8
		private const val B = 0.2

		/** 依次为 x,y 比例对，对应多个叠加图标。 */
		private val OVERLAY_POS = doubleArrayOf(A, B, B, B, A, A, B, A)
	}
}
