package kadx.gui.cache.usage

import kadx.api.plugins.input.data.IMethodRef

/**
 * 单个方法的 usage 数据。
 *
 * **做什么**：保存方法引用 [mthRef]，以及四类使用关系：
 * - [usage]：哪些方法使用了本方法（本方法的调用者）；
 * - [uses]：本方法使用了哪些方法（本方法调用的目标）；
 * - [unresolvedUsage]：本方法调用的、无法解析的方法签名；
 * - [callsSelf]：本方法是否递归调用自身。
 *
 * 这些列表初始为 `null`，由 [CollectUsageData] 采集后填入。
 */
internal class MthUsageData(
	val mthRef: MthRef,
) {
	/** 使用（调用）了本方法的方法列表。 */
	var usage: List<MthRef>? = null

	/** 本方法使用（调用）的方法列表。 */
	var uses: List<MthRef>? = null

	/** 本方法调用的、无法解析的方法引用列表。 */
	var unresolvedUsage: List<IMethodRef>? = null

	/** 本方法是否调用了自身。 */
	var callsSelf: Boolean = false
}
