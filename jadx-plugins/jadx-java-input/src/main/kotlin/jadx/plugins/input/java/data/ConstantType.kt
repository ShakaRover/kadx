package jadx.plugins.input.java.data

import jadx.plugins.input.java.utils.JavaClassParseException

/**
 * Class 文件常量池条目类型（JVM 规范 §4.4 的 tag 值）。
 *
 * **做什么**：每个枚举项对应一种常量池条目，携带两个关键信息——
 * [tag]（字节码中的单字节标识）和 [dataSize]（该条目除 tag 外占用的字节数，-1 表示变长如 UTF8）。
 * 解析器靠它快速跳过/定位常量池各条目。
 */
enum class ConstantType(tagValue: Int, val dataSize: Int) {

	UTF8(1, -1),
	INTEGER(3, 4),
	FLOAT(4, 4),
	LONG(5, 8),
	DOUBLE(6, 8),
	CLASS(7, 2),
	STRING(8, 2),
	FIELD_REF(9, 4),
	METHOD_REF(10, 4),
	INTERFACE_METHOD_REF(11, 4),
	NAME_AND_TYPE(12, 4),
	METHOD_HANDLE(15, 3),
	METHOD_TYPE(16, 2),
	DYNAMIC(17, 4),
	INVOKE_DYNAMIC(18, 4),
	MODULE(19, 2),
	PACKAGE(20, 2),
	;

	/** tag 字节值（原 Java 存为 byte，这里保持同样的取值范围） */
	val tag: Byte = tagValue.toByte()

	companion object {
		// tag → 枚举项 的查表数组（下标即 tag），避免每次线性查找
		private val TAG_MAP: Array<ConstantType?> = buildTagMap()

		private fun buildTagMap(): Array<ConstantType?> {
			var maxVal = -1
			for (value in entries) {
				if (value.tag.toInt() > maxVal) {
					maxVal = value.tag.toInt()
				}
			}
			val map = arrayOfNulls<ConstantType>(maxVal + 1)
			for (value in entries) {
				map[value.tag.toInt()] = value
			}
			return map
		}

		/**
		 * 按 tag 值查类型。
		 * @throws JavaClassParseException tag 不是合法的常量池条目类型时抛出
		 */
		@JvmStatic
		fun getTypeByTag(tag: Int): ConstantType {
			val type = TAG_MAP[tag]
			if (type == null) {
				throw JavaClassParseException("Unknown constant pool tag: " + tag)
			}
			return type
		}
	}
}
