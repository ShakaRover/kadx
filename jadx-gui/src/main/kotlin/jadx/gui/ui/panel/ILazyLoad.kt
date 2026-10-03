package jadx.gui.ui.panel

/**
 * 「懒加载」标记接口。
 *
 * **做什么**：资源面板内的某些标签页（例如十六进制/二进制视图）数据量较大，
 * 只有在用户真正切到该标签页时才需要加载。[ResourcePanel] 在标签页第一次被选中时
 * 检查该接口并调用 [loadData]。
 *
 * **为什么保留为接口**：由 Java/Kotlin 双方实现（如 `BinaryContentPanel`），
 * 方法签名必须与原 Java 完全一致，保证互操作零改动。
 */
interface ILazyLoad {

	/** 加载该组件所需的数据（在 Swing 事件线程上调用）。 */
	fun loadData()
}
