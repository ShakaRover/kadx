package jadx.gui.cache.usage

/**
 * 单个类的 usage 数据。
 *
 * **做什么**：保存类的原始名 [rawName]，以及：
 * - [clsDeps]：本类依赖的类；
 * - [clsUsage]：使用了本类的类；
 * - [clsUseInMth]：在哪些方法里使用了本类；
 * - [fldUsage] / [mthUsage]：字段 / 方法各自的 usage 明细（按短 id 索引）。
 *
 * 列表字段初始为 `null`，由 [CollectUsageData] 采集后填入。
 */
internal class ClsUsageData(
	val rawName: String,
) {
	/** 本类依赖的类列表（原始类名）。 */
	var clsDeps: List<String>? = null

	/** 使用了本类的类列表（原始类名）。 */
	var clsUsage: List<String>? = null

	/** 在哪些方法里使用了本类。 */
	var clsUseInMth: List<MthRef>? = null

	/** 字段 usage 明细，键为字段短 id。 */
	val fldUsage: MutableMap<String, FldUsageData> = HashMap()

	/** 方法 usage 明细，键为方法短 id。 */
	val mthUsage: MutableMap<String, MthUsageData> = HashMap()
}
