package jadx.core.xmlgen.entry

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
	private val id: Int,
	private val pkgName: String,
	private val typeName: String,
	private val keyName: String,
	private val config: String,
) {
	private var parentRef: Int = 0
	private var protoValue: ProtoValue? = null
	private var simpleValue: RawValue? = null
	private var namedValues: List<RawNamedValue>? = null

	fun copy(newKeyName: String): ResourceEntry {
		val copy = ResourceEntry(id, pkgName, typeName, newKeyName, config)
		copy.parentRef = this.parentRef
		copy.protoValue = this.protoValue
		copy.simpleValue = this.simpleValue
		copy.namedValues = this.namedValues
		return copy
	}

	fun copyWithId(resName: String): ResourceEntry = copy(String.format("%s_res_0x%08x", resName, id))

	fun getId(): Int = id

	fun getPkgName(): String = pkgName

	fun getTypeName(): String = typeName

	fun getKeyName(): String = keyName

	fun getConfig(): String = config

	fun setParentRef(parentRef: Int) {
		this.parentRef = parentRef
	}

	fun getParentRef(): Int = parentRef

	fun getProtoValue(): ProtoValue? = protoValue

	fun setProtoValue(protoValue: ProtoValue?) {
		this.protoValue = protoValue
	}

	fun getSimpleValue(): RawValue? = simpleValue

	fun setSimpleValue(simpleValue: RawValue?) {
		this.simpleValue = simpleValue
	}

	fun setNamedValues(namedValues: List<RawNamedValue>?) {
		this.namedValues = namedValues
	}

	fun getNamedValues(): List<RawNamedValue>? = namedValues

	override fun toString(): String = "  0x" + Integer.toHexString(id) + " (" + id + ')' + config + " = " + typeName + '.' + keyName
}
