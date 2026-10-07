package kadx.gui.utils

import kadx.commons.app.KadxCommonEnv
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 显式 `System.gc()` 的统一入口（默认**不执行**）。
 *
 * **为什么默认不执行**：`System.gc()` 是**全量 STW GC 的提示**。GUI 默认堆是
 * `MaxRAMPercentage=70` × 16 GB ≈ 11.2 GB，在这种堆上单次全量 GC 是**秒级卡顿**；
 * 实测（见会话 2 调研）在内存紧张 + swap 的情况下，单次 G1 full compaction 可达
 * **数十秒到上百秒**。而这些调用点原本只是「刚制造了垃圾，顺手压一下」——
 * G1 本来就会自行收集，不需要外部催促，却要用户付出界面冻结的代价。
 *
 * **保留开关**：`KADX_EXPLICIT_GC=true|false` 可强制覆盖（便于排查「到底是不是 GC 时机问题」）。
 * 未设置时用调用方给的 [run] 的 `enabledByDefault`。
 *
 * **唯一默认开启的调用点**：`BackgroundExecutor` 的低内存降级路径 —— 那里
 * `System.gc()` 是**决策依据**（先降线程数、再强制回收、然后根据回收后的可用内存
 * 决定「继续」还是「取消任务」）。去掉它会让更多任务被误判为内存不足而取消。
 */
internal object ExplicitGc {

	private val LOG: Logger = LoggerFactory.getLogger(ExplicitGc::class.java)

	const val ENV_VAR: String = "KADX_EXPLICIT_GC"

	private fun isEnabled(enabledByDefault: Boolean): Boolean {
		val raw = KadxCommonEnv.get(ENV_VAR, null)?.trim()?.lowercase()
		return when (raw) {
			null, "" -> enabledByDefault
			"true", "1", "on", "yes" -> true
			"false", "0", "off", "no" -> false
			else -> {
				LOG.warn("Unknown {} value '{}', using default ({})", ENV_VAR, raw, enabledByDefault)
				enabledByDefault
			}
		}
	}

	/**
	 * 按需执行显式 GC。
	 *
	 * @param reason 仅用于日志，说明调用点
	 * @param enabledByDefault 未设置 `KADX_EXPLICIT_GC` 时是否执行；默认 false
	 */
	fun run(reason: String, enabledByDefault: Boolean = false) {
		if (isEnabled(enabledByDefault)) {
			LOG.info("Running explicit System.gc(): {}", reason)
			System.gc()
		}
	}
}
