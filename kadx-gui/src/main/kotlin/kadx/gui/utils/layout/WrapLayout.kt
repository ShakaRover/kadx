package kadx.gui.utils.layout

import org.intellij.lang.annotations.MagicConstant
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * 支持换行的 [FlowLayout] 子类。
 *
 * **做什么**：当一行放不下时自动换行，并正确计算首选/最小尺寸；
 * 通过 [JScrollPane] 祖先检测避免首选宽度超过可视区域。
 */
class WrapLayout : FlowLayout {

	constructor() : super()

	constructor(@MagicConstant(valuesFromClass = FlowLayout::class) align: Int) : super(align)

	constructor(align: Int, hgap: Int, vgap: Int) : super(align, hgap, vgap)

	private var preferredLayoutSizeCache: Dimension? = null

	override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, true)

	override fun minimumLayoutSize(target: Container): Dimension {
		val minimum = layoutSize(target, false)
		minimum.width -= (getHgap() + 1)
		return minimum
	}

	private fun layoutSize(target: Container, preferred: Boolean): Dimension {
		synchronized(target.getTreeLock()) {
			// 每行必须适配容器宽度；容器宽度为 0 时向上查找，最终回退到最大宽度
			var container: Container = target
			while (container.getSize().width == 0) {
				val parent = container.getParent() ?: break
				container = parent
			}
			var targetWidth = container.getSize().width
			if (targetWidth == 0) {
				targetWidth = Int.MAX_VALUE
			}

			val hgap = getHgap()
			val vgap = getVgap()
			val insets: Insets = target.getInsets()
			val horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2)
			val maxWidth = targetWidth - horizontalInsetsAndGap

			val dim = Dimension(0, 0)
			var rowWidth = 0
			var rowHeight = 0
			val nmembers = target.getComponentCount()
			for (i in 0 until nmembers) {
				val m: Component = target.getComponent(i)
				if (m.isVisible) {
					val d = if (preferred) m.getPreferredSize() else m.getMinimumSize()
					val width = d.width
					// 当前行放不下则换行
					if (rowWidth + width >= maxWidth) {
						addRow(dim, rowWidth, rowHeight)
						rowWidth = 0
						rowHeight = 0
					}
					// 第一个之后的组件之间补水平间距
					if (rowWidth != 0) {
						rowWidth += hgap
					}
					rowWidth += width
					rowHeight = Math.max(rowHeight, d.height)
				}
			}
			addRow(dim, rowWidth, rowHeight)

			dim.width += horizontalInsetsAndGap
			dim.height += insets.top + insets.bottom + vgap * 2

			// 在滚动面板中，需要让首选宽度略小于容器宽度，保证缩小容器时布局正确
			val scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, target)
			if (scrollPane != null && target.isValid) {
				dim.width -= (hgap + 1)
			}
			return dim
		}
	}

	/**
	 * 一行结束，用该行尺寸更新容器的首选尺寸。
	 */
	private fun addRow(dim: Dimension, rowWidth: Int, rowHeight: Int) {
		dim.width = Math.max(dim.width, rowWidth)
		if (dim.height > 0) {
			dim.height += getVgap()
		}
		dim.height += rowHeight
	}

	companion object {
		private const val serialVersionUID = 6109752116520941346L
	}
}
