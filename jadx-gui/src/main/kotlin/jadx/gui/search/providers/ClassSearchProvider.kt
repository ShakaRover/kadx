package jadx.gui.search.providers

import jadx.api.JavaClass
import jadx.gui.jobs.Cancelable
import jadx.gui.search.SearchSettings
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow

/**
 * 类名搜索提供者。
 *
 * **做什么**：依次检查每个类，只要“短名 / 全名 / 别名全名 / 原始名”任意一个命中，
 * 就把该类作为结果返回。
 *
 * **为什么不是 `data class`**：它是带游标状态（[clsNum]）的有状态迭代器。
 */
class ClassSearchProvider(
	mw: MainWindow,
	searchSettings: SearchSettings,
	classes: List<JavaClass>,
) : BaseSearchProvider(mw, searchSettings, classes) {

	private var clsNum = 0

	override fun next(cancelable: Cancelable): JNode? {
		while (true) {
			if (cancelable.isCanceled || clsNum >= classes.size) {
				return null
			}
			val curCls = classes[clsNum++]
			if (checkCls(curCls)) {
				return convert(curCls)
			}
		}
	}

	private fun checkCls(cls: JavaClass): Boolean {
		val clsInfo = cls.getClassNode().classInfo
		return isMatch(clsInfo.shortName) ||
			isMatch(clsInfo.fullName) ||
			isMatch(clsInfo.aliasFullName) ||
			isMatch(clsInfo.rawName)
	}

	override fun progress(): Int = clsNum
}
