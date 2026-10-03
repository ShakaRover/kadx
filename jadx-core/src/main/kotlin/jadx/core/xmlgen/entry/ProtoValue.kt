package jadx.core.xmlgen.entry

/**
 * AAB（protobuf 资源表）中的值节点。
 *
 * 保留原 Java 的链式 setter（返回 `this`），因为 AAB 解析器仍以
 * `ProtoValue(...).setName(...).setType(...)` 形式构建值树。
 * 解析产物，按引用标识使用，故为普通类而非 `data class`。
 */
class ProtoValue(val value: String? = null) {
	private var parentValue: String? = null
	private var nameValue: String? = null
	private var typeValue: Int = 0
	private var namedValuesValue: List<ProtoValue>? = null

	val type: Int get() = typeValue

	fun setType(type: Int): ProtoValue {
		this.typeValue = type
		return this
	}

	val parent: String? get() = parentValue

	fun setParent(parent: String?): ProtoValue {
		this.parentValue = parent
		return this
	}

	fun setName(name: String?): ProtoValue {
		this.nameValue = name
		return this
	}

	val name: String? get() = nameValue

	fun setNamedValues(namedValues: List<ProtoValue>?): ProtoValue {
		this.namedValuesValue = namedValues
		return this
	}

	val namedValues: List<ProtoValue>? get() = namedValuesValue
}
