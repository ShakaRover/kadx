package jadx.gui.settings.ui.cache

import java.awt.Component
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * 缓存列表单元格渲染器：第一列显示项目名，第二列显示占用大小；
 * 并根据选中状态切换背景/前景色，提示文本为缓存目录路径。
 */
class CachesTableRenderer : TableCellRenderer {

	private val label: JLabel = JLabel()

	init {
		label.isOpaque = true
	}

	override fun getTableCellRendererComponent(
		table: JTable,
		value: Any?,
		isSelected: Boolean,
		hasFocus: Boolean,
		row: Int,
		column: Int,
	): Component {
		val obj = value as TableRow
		when (column) {
			0 -> label.text = obj.getProject()
			1 -> label.text = obj.getUsage()
		}
		label.toolTipText = obj.getCacheEntry().getCache()

		if (obj.isSelected()) {
			label.background = table.selectionBackground
			label.foreground = table.selectionForeground
		} else {
			label.background = table.background
			label.foreground = table.foreground
		}
		return label
	}
}
