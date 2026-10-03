package jadx.gui.search.providers

import jadx.api.JavaClass
import jadx.core.dex.info.FieldInfo
import jadx.gui.jobs.Cancelable
import jadx.gui.search.SearchSettings
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow

/**
 * 字段名搜索提供者。
 *
 * **做什么**：逐个类遍历字段，只要“名字 / 别名 / 完整 id”任意一个命中就返回该字段节点。
 *
 * **为什么不是 `data class`**：它带类/字段游标状态，属于有状态迭代器。
 */
class FieldSearchProvider(
	mw: MainWindow,
	searchSettings: SearchSettings,
	classes: List<JavaClass>,
) : BaseSearchProvider(mw, searchSettings, classes) {

	private var clsNum = 0
	private var fldNum = 0

	override fun next(cancelable: Cancelable): JNode? {
		while (true) {
			if (cancelable.isCanceled()) {
				return null
			}
			val cls = classes[clsNum]
			val fields = cls.getClassNode().fields
			if (fldNum < fields.size) {
				val fld = fields[fldNum++]
				if (checkField(fld.getFieldInfo())) {
					return convert(fld)
				}
			} else {
				clsNum++
				fldNum = 0
				if (clsNum >= classes.size) {
					return null
				}
			}
		}
	}

	private fun checkField(fieldInfo: FieldInfo): Boolean = isMatch(fieldInfo.name) ||
		isMatch(fieldInfo.alias) ||
		isMatch(fieldInfo.fullId)

	override fun progress(): Int = clsNum
}
