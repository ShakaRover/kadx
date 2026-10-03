package jadx.gui.search.providers

import jadx.api.JavaClass
import jadx.core.dex.info.MethodInfo
import jadx.gui.jobs.Cancelable
import jadx.gui.search.SearchSettings
import jadx.gui.treemodel.JNode
import jadx.gui.ui.MainWindow

/**
 * 方法名搜索提供者。
 *
 * **做什么**：逐个类遍历方法，只要“短签名 / 别名 / 完整 id / 别名全名”任意一个命中
 * 就返回该方法节点。
 *
 * **为什么不是 `data class`**：它带类/方法游标状态，属于有状态迭代器。
 */
class MethodSearchProvider(
	mw: MainWindow,
	searchSettings: SearchSettings,
	classes: List<JavaClass>,
) : BaseSearchProvider(mw, searchSettings, classes) {

	private var clsNum = 0
	private var mthNum = 0

	override fun next(cancelable: Cancelable): JNode? {
		if (classes.isEmpty()) {
			return null
		}
		while (true) {
			if (cancelable.isCanceled) {
				return null
			}
			val cls = classes[clsNum]
			val methods = cls.getClassNode().methods
			if (mthNum < methods.size) {
				val mth = methods[mthNum++]
				if (checkMth(mth.methodInfo)) {
					return convert(mth)
				}
			} else {
				clsNum++
				mthNum = 0
				if (clsNum >= classes.size) {
					return null
				}
			}
		}
	}

	private fun checkMth(mthInfo: MethodInfo): Boolean = isMatch(mthInfo.shortId) ||
		isMatch(mthInfo.alias) ||
		isMatch(mthInfo.fullId) ||
		isMatch(mthInfo.aliasFullName)

	override fun progress(): Int = clsNum
}
