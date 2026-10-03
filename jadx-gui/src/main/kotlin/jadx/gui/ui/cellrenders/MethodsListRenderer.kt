package jadx.gui.ui.cellrenders

import jadx.api.JavaMethod
import jadx.gui.utils.UiUtils
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * 方法选择列表的渲染器。
 *
 * **做什么**：左侧一个复选框、右侧方法签名文本（带返回类型与图标），
 * 并根据选中状态切换前景/背景色。
 *
 * **为什么保留 `ListCellRenderer` 接口签名**：Swing 的 `JList` 会以接口方法回调，
 * 参数与返回类型必须与原来一致。
 */
class MethodsListRenderer :
	JPanel(),
	ListCellRenderer<JavaMethod> {
	private val checkBox: JCheckBox = JCheckBox()
	private val label: JLabel = JLabel()

	init {
		layout = BorderLayout(5, 0)
		border = BorderFactory.createEmptyBorder(1, 5, 1, 5)

		add(checkBox, BorderLayout.WEST)
		add(label, BorderLayout.CENTER)

		isOpaque = true

		checkBox.isOpaque = false
		label.isOpaque = false
	}

	override fun getListCellRendererComponent(
		list: JList<out JavaMethod>,
		value: JavaMethod,
		index: Int,
		isSelected: Boolean,
		cellHasFocus: Boolean,
	): Component {
		label.text = UiUtils.typeFormatHtml(MethodRenderHelper.makeBaseString(value), value.getReturnType())
		label.icon = MethodRenderHelper.getIcon(value)

		checkBox.isSelected = isSelected

		background = if (isSelected) list.selectionBackground else list.background
		foreground = if (isSelected) list.selectionForeground else list.foreground
		label.foreground = if (isSelected) list.selectionForeground else list.foreground

		return this
	}
}
