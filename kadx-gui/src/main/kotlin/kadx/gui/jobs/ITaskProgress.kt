package kadx.gui.jobs

/**
 * 任务进度接口。
 *
 * **做什么**：为进度条等 UI 组件提供“当前进度 / 总量”两个只读值。
 *
 * **为什么方法名不是 `getXxx`**：原 Java 接口就用 `progress()` / `total()`，
 * 保持显式函数签名，Java 调用方（如 `ProgressPanel`、`SearchDialog`）零改动。
 */
interface ITaskProgress {

	/** 当前已完成的数量。 */
	fun progress(): Int

	/** 总量（用于计算百分比）。 */
	fun total(): Int
}
