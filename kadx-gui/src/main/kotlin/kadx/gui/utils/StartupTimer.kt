package kadx.gui.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory

/**
 * GUI 启动分段计时（P5-A1）。
 *
 * **做什么**：在关键相位打一条 INFO 日志（相位名 + 距 **JVM 启动**的毫秒数），
 * 用于把「二次启动慢」拆开看：`load`（dex 解析 / 类树构建 / classpath 初始化）、
 * `tree`（类树模型填充）、`initial-view`（首个视图就绪）。
 *
 * **为什么需要它**：GUI 原本**完全没有**这些打点，导致「启动慢」无法归因 ——
 * 既不知道是 load 还是 UI，也无法验证磁盘代码缓存到底帮了多少。
 *
 * **为什么用 JVM uptime**：时间原点必须是进程启动时刻，而不是本对象被首次访问的时刻
 * （`object` 的初始化是惰性的，可能晚于启动很多）。`RuntimeMXBean.uptime` 直接给出
 * 「JVM 已运行毫秒数」，无需自己维护状态。
 *
 * **不改行为**：纯日志。
 */
object StartupTimer {

	private val LOG: Logger = LoggerFactory.getLogger(StartupTimer::class.java)

	/** 距 JVM 启动的毫秒数。 */
	fun elapsedMs(): Long = ManagementFactory.getRuntimeMXBean().uptime

	/** 打一条相位日志：`GUI startup phase: <phase> at <elapsed> ms (JVM uptime)`。 */
	fun mark(phase: String) {
		LOG.info("GUI startup phase: {} at {} ms (JVM uptime)", phase, elapsedMs())
	}
}
