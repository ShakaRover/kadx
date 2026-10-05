package kadx.core.dex.visitors.typeinference

/**
 * 单次类型更新的处理结果。
 *
 * - [REJECT]：候选类型被拒绝，不应用；
 * - [SAME]：候选类型与当前类型一致，无需改动；
 * - [CHANGED]：类型已发生变化（可能已记录到 [TypeUpdateInfo]）。
 *
 * **Kotlin 转换说明**：枚举常量在 JVM 上仍是静态字段，Java 调用方零改动。
 */
enum class TypeUpdateResult {
	REJECT,
	SAME,
	CHANGED,
}
