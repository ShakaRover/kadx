package kadx.gui.search.providers

import kadx.api.KadxDecompiler
import kadx.api.JavaClass
import kadx.api.JavaNode
import kadx.core.dex.nodes.ICodeNode
import kadx.gui.search.ISearchMethod
import kadx.gui.search.ISearchProvider
import kadx.gui.search.SearchSettings
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JNode
import kadx.gui.ui.MainWindow
import kadx.gui.utils.JNodeCache

/**
 * 搜索提供者的公共基类。
 *
 * **做什么**：把“类列表 + 搜索设置 + 节点缓存”这些所有提供者都要用的东西准备好，
 * 并提供 `isMatch` / `convert` 两个便捷方法。子类只需实现 [next]。
 *
 * **为什么 `searchMth` / `searchStr` / `classes` 是 `protected`**：子类（类/方法/字段/
 * 代码搜索）需要直接访问，与原 Java 的 `protected final` 字段语义一致。
 *
 * **为什么不是 `data class`**：它持有缓存与搜索状态，需要按身份使用。
 */
abstract class BaseSearchProvider(
	mw: MainWindow,
	protected val searchSettings: SearchSettings,
	classes: List<JavaClass>,
) : ISearchProvider {

	private val nodeCache: JNodeCache = mw.getCacheObject().nodeCache
	private val decompiler: KadxDecompiler = mw.getWrapper().getDecompiler()

	protected val searchMth: ISearchMethod = searchSettings.getSearchMethod()
	protected val searchStr: String = searchSettings.getSearchString()
	protected val classes: List<JavaClass>

	init {
		val searchPackage = searchSettings.getSearchPackage()
		this.classes = if (searchPackage != null) {
			// 限定搜索包时，只保留该包（含子包）下的类
			classes.filter { it.getJavaPackage()?.isDescendantOf(searchPackage) == true }
		} else {
			classes
		}
	}

	/** 判断字符串是否命中搜索条件。 */
	protected fun isMatch(str: String): Boolean = searchMth.find(str, searchStr, 0) != -1

	/** 由 Java 节点构造界面节点。 */
	protected fun convert(node: JavaNode): JNode = checkNotNull(nodeCache.makeFrom(node))

	/** 由 [JavaClass] 构造界面类节点。 */
	protected fun convert(cls: JavaClass): JClass = checkNotNull(nodeCache.makeFrom(cls))

	/** 由代码节点构造界面节点（需要先反查对应的 Java 节点）。 */
	protected fun convert(codeNode: ICodeNode): JNode {
		val node = checkNotNull(decompiler.getJavaNodeByRef(codeNode))
		return checkNotNull(nodeCache.makeFrom(node))
	}

	override fun total(): Int = classes.size
}
