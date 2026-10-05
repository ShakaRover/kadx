package kadx.core.xmlgen.entry

/**
 * 一条资源条目。
 *
 * 32 位资源 id（AOSP `make_resid()`）：
 * 1. Package ID（8 bit）
 * 2. Type ID（8 bit）
 * 3. Entry ID（16 bit）
 *
 * 解析产物，按引用标识使用（`ResourceStorage` 以它为 TreeMap 键、并以 `indexOf` 定位），
 * 因此保持普通类，不生成 `data class` 的 `equals/hashCode`。
 */
class ResourceEntry(
	val id: Int,
	val pkgName: String,
	val typeName: String,
	val keyName: String,
	val config: String,
) {
	var parentRef: Int = 0
	var protoValue: ProtoValue? = null
	var simpleValue: RawValue? = null
	var namedValues: List<RawNamedValue>? = null

	fun copy(newKeyName: String): ResourceEntry {
		val copy = ResourceEntry(id, pkgName, typeName, newKeyName, config)
		copy.parentRef = this.parentRef
		copy.protoValue = this.protoValue
		copy.simpleValue = this.simpleValue
		copy.namedValues = this.namedValues
		return copy
	}

	fun copyWithId(resName: String): ResourceEntry = copy(String.format("%s_res_0x%08x", resName, id))

	override fun toString(): String = "  0x" + Integer.toHexString(id) + " (" + id + ')' + config + " = " + typeName + '.' + keyName
}
