package kadx.gui.settings.ui.cache

import kadx.gui.utils.NLS
import javax.swing.table.AbstractTableModel

/**
 * 缓存列表的表格数据模型。
 *
 * **做什么**：保存 [TableRow] 列表，向 [javax.swing.JTable] 提供行数、列名与单元格值。
 * 两列分别显示项目名与占用大小，单元格值即整行 [TableRow]（由渲染器决定显示内容）。
 */
class CachesTableModel : AbstractTableModel() {

	@Transient
	private var rows: List<TableRow> = emptyList()

	fun setRows(list: List<TableRow>) {
		this.rows = list
	}

	fun getRows(): List<TableRow> = rows

	override fun getRowCount(): Int = rows.size

	override fun getColumnCount(): Int = 2

	override fun getColumnName(index: Int): String = COLUMN_NAMES[index]

	override fun getColumnClass(columnIndex: Int): Class<*> = TableRow::class.java

	override fun getValueAt(rowIndex: Int, columnIndex: Int): TableRow = rows[rowIndex]

	fun changeSelection(idx: Int) {
		val row = rows[idx]
		row.setSelected(!row.isSelected)
	}

	companion object {
		private const val serialVersionUID: Long = -7725573085995496397L

		private val COLUMN_NAMES = arrayOf(
			NLS.str("preferences.cache.table.project"),
			NLS.str("preferences.cache.table.size"),
		)
	}
}
