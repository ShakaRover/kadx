package jadx.core.xmlgen.entry

/**
 * AAB（protobuf 资源表）中的值节点。
 *
 * 保留原 Java 的链式 setter（返回 `this`），因为 AAB 解析器仍以
 * `ProtoValue(...).setName(...).setType(...)` 形式构建值树。
 * 解析产物，按引用标识使用，故为普通类而非 `data class`。
 */
class ProtoValue(private var value: String? = null) {
	private var parent: String? = null
	private var name: String? = null
	private var type: Int = 0
	private var namedValues: List<ProtoValue>? = null

	fun getType(): Int = type

	fun setType(type: Int): ProtoValue {
		this.type = type
		return this
	}

	fun getValue(): String? = value

	fun getParent(): String? = parent

	fun setParent(parent: String?): ProtoValue {
		this.parent = parent
		return this
	}

	fun setName(name: String?): ProtoValue {
		this.name = name
		return this
	}

	fun getName(): String? = name

	fun setNamedValues(namedValues: List<ProtoValue>?): ProtoValue {
		this.namedValues = namedValues
		return this
	}

	fun getNamedValues(): List<ProtoValue>? = namedValues
}
