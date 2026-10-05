package kadx.gui.ui.panel

import kadx.core.dex.instructions.args.ArgType
import kadx.gui.ui.panel.JDebuggerPanel.ValueTreeNode

/**
 * 调试器控制器接口。
 *
 * **做什么**：把「调试会话」的启停、单步、暂停/继续、修改变量值等操作抽象出来，
 * 由 `DebugController` 实现，供 [JDebuggerPanel]、`VarTreePopupMenu`、`SetValueDialog`
 * 等 UI 组件调用。
 *
 * **为什么全部保留显式函数**：这是被 Java 与 Kotlin 双方实现的接口，
 * 方法名/签名必须与原 Java 完全一致（例如 `isSuspended()`、`getProcessName()`），
 * 保证 Java 实现类零改动。
 */
interface IDebugController {

	/** 连接设备并启动调试会话。 */
	fun startDebugger(debuggerPanel: JDebuggerPanel, adbHost: String, adbPort: Int, androidVer: Int): Boolean

	/** 继续运行。 */
	fun run(): Boolean

	/** 单步跳过。 */
	fun stepOver(): Boolean

	/** 单步进入。 */
	fun stepInto(): Boolean

	/** 单步跳出。 */
	fun stepOut(): Boolean

	/** 暂停。 */
	fun pause(): Boolean

	/** 停止调试。 */
	fun stop(): Boolean

	/** 退出调试会话。 */
	fun exit(): Boolean

	/** 当前是否处于挂起（暂停）状态。 */
	fun isSuspended(): Boolean

	/** 当前是否处于调试中。 */
	fun isDebugging(): Boolean

	/** 修改寄存器/字段的值。 */
	fun modifyRegValue(node: ValueTreeNode, type: ArgType, value: Any?): Boolean

	/** 当前调试进程名。 */
	fun getProcessName(): String

	/** 注册状态变化监听器。 */
	fun setStateListener(l: StateListener)

	/**
	 * 调试状态监听器。
	 *
	 * 保留为嵌套接口，Java 侧 `DebugController.StateListener` / `IDebugController.StateListener`
	 * 两种写法均可用。
	 */
	interface StateListener {
		/**
		 * 状态变化回调。
		 *
		 * @param suspended 是否挂起
		 * @param stopped   是否已停止
		 */
		fun onStateChanged(suspended: Boolean, stopped: Boolean)
	}
}
