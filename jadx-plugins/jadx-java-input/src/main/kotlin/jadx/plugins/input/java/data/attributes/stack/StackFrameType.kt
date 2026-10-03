package jadx.plugins.input.java.data.attributes.stack

import org.jetbrains.annotations.Nullable

/**
 * StackMapTable 帧类型（JVMS §4.10.5.5），按帧头字节值区间划分。
 *
 **做什么**：把帧头 u1 数据映射到帧种类；不同种类的帧携带不同的后续字段，
 * [StackMapTableReader] 据此分派到对应的读取逻辑。
 */
enum class StackFrameType(private val start: Int, private val end: Int) {

	SAME_FRAME(0, 63),
	SAME_LOCALS_1_STACK(64, 127),
	SAME_LOCALS_1_STACK_EXTENDED(247, 247),
	CHOP(248, 250),
	SAME_FRAME_EXTENDED(251, 251),
	APPEND(252, 254),
	FULL(255, 255),
	;

	companion object {
		// 帧头字节值 → 类型 的查表数组（下标即 u1 数据）
		private val MAPPING: Array<StackFrameType?> = buildMapping()

		private fun buildMapping(): Array<StackFrameType?> {
			val mapping = arrayOfNulls<StackFrameType>(256)
			for (value in entries) {
				for (i in value.start..value.end) {
					mapping[i] = value
				}
			}
			return mapping
		}

		/** @return [data] 对应的帧类型；非法值返回 null */
		@Nullable
		fun getType(data: Int): StackFrameType? = MAPPING[data]
	}
}
