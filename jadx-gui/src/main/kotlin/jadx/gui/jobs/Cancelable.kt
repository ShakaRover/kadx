package jadx.gui.jobs

/**
 * 可取消对象接口。
 *
 * **做什么**：定义“查询是否已取消 / 请求取消”以及取消时的两个超时参数。
 * [IBackgroundTask] 继承本接口，因此所有后台任务都具备取消能力。
 *
 * **为什么保留 `getCancelTimeoutMS` / `getShutdownTimeoutMS` 方法名**：
 * 原 Java 接口的方法名即带 `get` 前缀，保持显式函数以兼容 Java 覆写方
 * （如 `SearchTask`）。默认值 2000ms / 5000ms 与原实现一致。
 */
interface Cancelable {

	/** 是否已被取消。 */
	fun isCanceled(): Boolean

	/** 请求取消。 */
	fun cancel()

	/** 取消后等待任务自行结束的超时时间（毫秒），默认 2000。 */
	fun getCancelTimeoutMS(): Int = 2000

	/** 强制关闭内部线程池的等待超时时间（毫秒），默认 5000。 */
	fun getShutdownTimeoutMS(): Int = 5000
}
