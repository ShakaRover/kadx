package jadx.api.plugins.events

/**
 * 事件对象接口：所有通过 [IJadxEvents] 发送的事件都实现本接口。
 *
 * **做什么**：通过 [getType] 返回该事件所属的类型；事件总线据此找到对应的监听器列表。
 *
 * **为什么保留显式 getter**：这是插件公共 API，Java 插件和 jadx-gui 中的 Java 事件类
 * （如 `TreeUpdate`）都直接调用/覆写 `getType()`，因此保持方法名与 JVM 签名不变。
 */
interface IJadxEvent {

	/**
	 * 返回事件类型。
	 *
	 * 使用 `out` 投影对应原 Java 的 `JadxEventType<? extends IJadxEvent>`：
	 * 这样具体事件（如 [jadx.api.plugins.events.types.NodeRenamedByUser]）可以返回
	 * `JadxEventType<NodeRenamedByUser>`，Java 子类的协变返回值依然合法。
	 */
	fun getType(): JadxEventType<out IJadxEvent>
}
