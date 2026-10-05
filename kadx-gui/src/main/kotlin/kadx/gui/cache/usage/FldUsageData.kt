package kadx.gui.cache.usage

/**
 * 单个字段的 usage 数据。
 *
 * **做什么**：保存字段引用 [fldRef]，以及“该字段被哪些方法使用”的列表 [usage]。
 * 列表初始为 `null`，由 [CollectUsageData] 在遍历 usage 结果时填入。
 */
internal class FldUsageData(
	val fldRef: FldRef,
) {
	/** 使用该字段的方法列表；未采集到数据时为 `null`（与原 Java 一致）。 */
	var usage: List<MthRef>? = null
}
