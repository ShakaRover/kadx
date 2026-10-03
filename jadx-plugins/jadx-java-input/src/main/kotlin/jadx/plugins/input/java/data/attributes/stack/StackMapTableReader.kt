package jadx.plugins.input.java.data.attributes.stack

import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader
import jadx.plugins.input.java.data.attributes.types.StackMapTableAttr
import jadx.plugins.input.java.utils.JavaClassParseException
import java.util.EnumMap
import java.util.HashMap

/**
 * StackMapTable attribute 读取器：解析 JDK7+ 字节码的验证器栈帧表。
 *
 **做什么**：按帧头 u1 分派到 7 种帧类型的读取逻辑，产出"偏移 → [StackFrame]"映射；
 * 相对帧（same/chop/append）基于前一帧推导局部变量数与偏移增量。
 */
class StackMapTableReader : IJavaAttributeReader {

	override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute {
		val count = reader.readU2()
		val map = HashMap<Int, StackFrame>(count)
		var prevFrame: StackFrame? = null
		for (i in 0 until count) {
			val frame = readFrame(reader, prevFrame)
			map[frame.offset] = frame
			prevFrame = frame
		}
		return StackMapTableAttr(map)
	}

	private fun readFrame(reader: DataReader, prevFrame: StackFrame?): StackFrame {
		val typeData = reader.readU1()
		val frameType = StackFrameType.getType(typeData)
		// 原 Java 直接 FRAME_READERS.get(frameType)，null 键返回 null；这里保持同样分派结果
		val frameReader = frameType?.let { FRAME_READERS[it] }
		if (frameReader == null) {
			throw JavaClassParseException("Found unsupported stack frame type: " + frameType)
		}
		val frameContext = FrameContext(reader, typeData, prevFrame)
		frameReader(frameContext)
		return frameContext.frame!! // 原 Java Objects.requireNonNull，同样 NPE
	}

	companion object {
		private val FRAME_READERS: EnumMap<StackFrameType, (FrameContext) -> Unit?> = registerReaders()

		private fun registerReaders(): EnumMap<StackFrameType, (FrameContext) -> Unit?> {
			val map = EnumMap<StackFrameType, (FrameContext) -> Unit?>(StackFrameType::class.java)
			map[StackFrameType.SAME_FRAME] = { readSame(it, false) }
			map[StackFrameType.SAME_FRAME_EXTENDED] = { readSame(it, true) }
			map[StackFrameType.SAME_LOCALS_1_STACK] = { readSL1S(it, false) }
			map[StackFrameType.SAME_LOCALS_1_STACK_EXTENDED] = { readSL1S(it, true) }
			map[StackFrameType.CHOP] = { readChop(it) }
			map[StackFrameType.APPEND] = { readAppend(it) }
			map[StackFrameType.FULL] = { readFull(it) }
			return map
		}

		private fun readSame(context: FrameContext, extended: Boolean) {
			val type: StackFrameType
			val offsetDelta: Int
			if (extended) {
				type = StackFrameType.SAME_FRAME_EXTENDED
				offsetDelta = context.dataReader.readU2()
			} else {
				type = StackFrameType.SAME_FRAME
				offsetDelta = context.typeData
			}
			val frame = StackFrame(calcOffset(context, offsetDelta), type)
			frame.stackSize = 0
			frame.localsCount = getPrevLocalsCount(context)
			context.frame = frame
		}

		private fun readSL1S(context: FrameContext, extended: Boolean) {
			val reader = context.dataReader
			val type: StackFrameType
			val offsetDelta: Int
			if (extended) {
				type = StackFrameType.SAME_LOCALS_1_STACK_EXTENDED
				offsetDelta = reader.readU2()
			} else {
				type = StackFrameType.SAME_LOCALS_1_STACK
				offsetDelta = context.typeData - 64
			}
			val stackTypes = TypeInfoReader.readTypeInfoList(reader, 1)
			val frame = StackFrame(calcOffset(context, offsetDelta), type)
			frame.stackSize = 1
			frame.stackValueTypes = stackTypes
			frame.localsCount = getPrevLocalsCount(context)
			context.frame = frame
		}

		private fun readChop(context: FrameContext) {
			val k = 251 - context.typeData
			val offsetDelta = context.dataReader.readU2()
			val frame = StackFrame(calcOffset(context, offsetDelta), StackFrameType.CHOP)
			frame.stackSize = 0
			frame.localsCount = getPrevLocalsCount(context) - k
			context.frame = frame
		}

		private fun readAppend(context: FrameContext) {
			val reader = context.dataReader
			val k = context.typeData - 251
			val offsetDelta = reader.readU2()
			TypeInfoReader.skipTypeInfoList(reader, k)
			val frame = StackFrame(calcOffset(context, offsetDelta), StackFrameType.APPEND)
			frame.stackSize = 0
			frame.localsCount = getPrevLocalsCount(context) - k
			context.frame = frame
		}

		private fun readFull(context: FrameContext) {
			val reader = context.dataReader
			val offsetDelta = reader.readU2()
			val localsCount = reader.readU2()
			TypeInfoReader.skipTypeInfoList(reader, localsCount)
			val stackSize = reader.readU2()
			val stackTypes = TypeInfoReader.readTypeInfoList(reader, stackSize)

			val frame = StackFrame(calcOffset(context, offsetDelta), StackFrameType.FULL)
			frame.localsCount = localsCount
			frame.stackSize = stackSize
			frame.stackValueTypes = stackTypes
			context.frame = frame
		}

		private fun calcOffset(context: FrameContext, offsetDelta: Int): Int {
			val prevFrame = context.prevFrame
			if (prevFrame == null) {
				return offsetDelta
			}
			return prevFrame.offset + offsetDelta + 1
		}

		private fun getPrevLocalsCount(context: FrameContext): Int {
			val prevFrame = context.prevFrame
			if (prevFrame == null) {
				return 0
			}
			return prevFrame.localsCount
		}

		/**
		 * 单次帧解析的上下文：读头、帧头数据、前一帧，以及解析结果槽位。
		 * （原 Java private static 嵌套类）
		 */
		private class FrameContext(
			val dataReader: DataReader,
			val typeData: Int,
			val prevFrame: StackFrame?,
		) {
			var frame: StackFrame? = null
		}
	}
}
