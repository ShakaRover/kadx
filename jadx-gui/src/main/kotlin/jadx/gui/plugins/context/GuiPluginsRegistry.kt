package jadx.gui.plugins.context

import jadx.gui.settings.data.ITabStatePersist
import javax.swing.Action

/**
 * 某个插件作用域（全局或项目）内注册的界面扩展点集合。
 *
 * **做什么**：分别收集菜单动作、代码区弹窗动作、树节点弹窗项、输入分类器
 * 与标签页状态适配器；[clear] 用于项目关闭时释放项目级扩展点。
 *
 * **Java 互操作**：`val` 属性生成 `getMenuActions()` / `getCodePopupActions()` 等 getter，
 * 与原 Java API 一致。
 */
class GuiPluginsRegistry {

	val menuActions: MutableList<Action> = ArrayList()
	val codePopupActions: MutableList<CodePopupAction> = ArrayList()
	val treePopupMenuEntries: MutableList<TreePopupMenuEntry> = ArrayList()
	val treeInputCategories: MutableList<ITreeInputCategory> = ArrayList()
	val tabStatePersistAdapters: MutableList<ITabStatePersist> = ArrayList()

	fun clear() {
		menuActions.clear()
		codePopupActions.clear()
		treePopupMenuEntries.clear()
		treeInputCategories.clear()
		tabStatePersistAdapters.clear()
	}
}
