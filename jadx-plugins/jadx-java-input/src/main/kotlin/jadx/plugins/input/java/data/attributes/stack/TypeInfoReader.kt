package jadx.plugins.input.java.data.attributes.stack

import jadx.plugins.input.java.data.DataReader

/**
 * 帧内 type_info 列表读取器（JVMS §4.10.5.5 的验证类型编码）。
 *
 **做什么**：type_info 是"u1 tag (+ u2 class 索引)"变长序列；本类只关心宽度——
 * double/long 记为 [StackValueType.WIDE]，其余（含带类索引的 object/uninitialized）都是 NARROW。
 */
class TypeInfoReader {

	companion object {
		private const val ITEM_TOP = 0
		private const val ITEM_INT = 1
		private const val ITEM_FLOAT = 2

		private const val ITEM_DOUBLE = 3
		private const val ITEM_LONG = 4

		private const val ITEM_NULL = 5
		private const val ITEM_UNINITIALIZED_THIS = 6

		private const val ITEM_OBJECT = 7
		private const val ITEM_UNINITIALIZED = 8

		/** 读 [count] 个 type_info，返回每个槽位的宽度 */
		internal fun readTypeInfoList(reader: DataReader, count: Int): Array<StackValueType> {
			val types = Array(count) { StackValueType.NARROW }
			for (i in 0 until count) {
				val tag = reader.readU1()
				val type = when (tag) {
					ITEM_DOUBLE, ITEM_LONG -> StackValueType.WIDE

					ITEM_OBJECT, ITEM_UNINITIALIZED -> {
						reader.readU2() // ignore class index
						StackValueType.NARROW
					}

					else -> StackValueType.NARROW
				}
				types[i] = type
			}
			return types
		}

		/** 跳过 [count] 个 type_info（只推进读头，不保留数据） */
		internal fun skipTypeInfoList(reader: DataReader, count: Int) {
			for (i in 0 until count) {
				val tag = reader.readU1()
				if (tag == ITEM_OBJECT || tag == ITEM_UNINITIALIZED) {
					reader.readU2()
				}
			}
		}
	}
}
