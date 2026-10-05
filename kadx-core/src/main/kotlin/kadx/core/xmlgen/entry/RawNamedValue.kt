package kadx.core.xmlgen.entry

/**
 * 资源表中的“名称引用 + 值”对。
 *
 * [nameRef] 是名称字符串在字符串池中的索引，[rawValue] 是原始值。
 * 这是解析产物，按引用标识使用，故为普通类而非 `data class`。
 */
class RawNamedValue(
	val nameRef: Int,
	val rawValue: RawValue,
) {
	override fun toString(): String = "RawNamedValue{nameRef=$nameRef, rawValue=$rawValue}"
}
