package kadx.api.utils.tasks

import java.util.concurrent.ExecutorService

/**
 * 任务执行器接口：把任务按“阶段（stage）”组织，支持并行或串行执行
 * （类似 fork-join 的简化模型）。
 *
 * **做什么**：
 * - `addParallelTasks` / `addSequentialTasks` / `addSequentialTask`：追加阶段；
 * - `execute` / `terminate` / `awaitTermination`：启动、停止、等待；
 * - `getTasksCount` / `getProgress` / `getThreadsCount` 等：查询进度与状态。
 *
 * **为什么保持接口方法形态**：本接口的实现与调用遍布 kadx-core / kadx-gui，
 * 方法名必须与原 Java 一致（显式 `getX` / `isX` 函数），Java 调用方零改动。
 * 参数中的 [Runnable] 是非 final 类型，Kotlin `List<Runnable>` 生成的 JVM 签名
 * 与原 Java 的 `List<? extends Runnable>` 相同。
 */
interface ITaskExecutor {

	/** 追加一个并行阶段，阶段内任务并发执行。 */
	fun addParallelTasks(parallelTasks: List<Runnable>)

	/** 追加一个串行阶段，阶段内任务依次执行。 */
	fun addSequentialTasks(seqTasks: List<Runnable>)

	/** 追加一个只含单个任务的串行阶段。 */
	fun addSequentialTask(task: Runnable)

	/** 已调度的任务总数。 */
	fun getTasksCount(): Int

	/**
	 * 设置并行阶段的线程数（执行过程中也可修改）。
	 * 默认为处理器数量的一半。
	 */
	fun setThreadsCount(threadsCount: Int)

	/** 当前并行阶段的线程数。 */
	fun getThreadsCount(): Int

	/** 开始执行任务。 */
	fun execute()

	/** 已完成的任务数。 */
	fun getProgress(): Int

	/** 调用后，尚未开始的任务将不再执行。 */
	fun terminate()

	/** 是否正在终止。 */
	fun isTerminating(): Boolean

	/** 是否正在运行。 */
	fun isRunning(): Boolean

	/** 阻塞直到执行结束。 */
	fun awaitTermination()

	/** 返回内部使用的线程池；尚未创建时返回 `null`。 */
	fun getInternalExecutor(): ExecutorService?
}
