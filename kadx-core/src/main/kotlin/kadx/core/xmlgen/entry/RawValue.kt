package kadx.core.xmlgen.entry

/**
 * 资源表中的原始值。
 *
 * [dataType] 为类型标签（如 `TYPE_INT_DEC`），[data] 为原始 32 位数据（含义随类型变化）。
 * 解析产物，按引用标识使用，故为普通类而非 `data class`。
 */
class RawValue(
	val dataType: Int,
	val data: Int,
) {
	override fun toString(): String = "RawValue: type=0x" + Integer.toHexString(dataType) + ", value=" + data
}
