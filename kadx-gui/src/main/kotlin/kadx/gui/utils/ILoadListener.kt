package kadx.gui.utils

/**
 * 文件/工程加载状态的监听器接口。
 *
 * **做什么**：当后台把文件或工程加载完成时，回调 [update] 通知监听者。
 *
 * **为什么要保留为 Java 可实现的接口**：原 Java 中由若干匿名类/类实现该接口，
 * 迁移后 Java 调用方仍需能实现它，因此保持普通接口形态与精确方法签名。
 */
interface ILoadListener {

	/**
	 * 更新文件/工程的加载状态。
	 *
	 * @return 返回 true 表示可以从监听列表中移除该监听器（一次性监听）
	 */
	fun update(loaded: Boolean): Boolean
}
