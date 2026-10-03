package jadx.gui.settings.data

import jadx.gui.treemodel.JNode

/**
 * 标签页状态持久化适配器接口，允许插件保存/恢复自定义节点的打开状态。
 *
 * **做什么**：`TabStateViewAdapter` 在保存项目时对无法识别的节点类型会查找这里注册的适配器，
 * 由插件把节点转成字符串（[save]），并在加载项目时再还原成节点（[load]）。
 *
 * **为什么保持为普通接口 + 显式 `getXxx()` 函数**：实现方在插件模块中（可能仍是 Java），
 * 保留原方法名与签名可以保证 Java 实现零改动；`load` 允许返回 `null` 表示无法还原。
 */
interface ITabStatePersist {

	/** 本适配器负责的节点类型。 */
	fun getNodeClass(): Class<out JNode>

	/** 把节点保存成字符串状态。 */
	fun save(node: JNode): String

	/** 从字符串状态还原节点；无法还原时返回 `null`。 */
	fun load(stateStr: String): JNode?
}
