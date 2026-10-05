package kadx.core.dex.instructions

import kadx.api.plugins.input.insns.custom.IArrayPayload
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.LiteralArg
import kadx.core.dex.instructions.args.PrimitiveType
import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.Arrays

/**
 * `fill-array-data` 指令携带的原始数组数据块。
 *
 * 例如 `int[] a = {1, 2, 3};` 会被编译成“先 new-array，再用 fill-array-data 写入
 * 常量数组”。这里保存原始字节/短整型/整型/长整型数组以及元素大小。
 *
 * Kotlin 转换说明：
 * - [data] 来自 `IArrayPayload.getData()`，其 Kotlin 签名是可空的，故这里也标为 `Any?`；
 * - 元素读取时按 [elemSize] 把 [data] 强转成对应的基本类型数组，与 Java 行为一致。
 */
class FillArrayData private constructor(
	val data: Any?,
	val size: Int,
	private val elemSize: Int,
) : InsnNode(InsnType.FILL_ARRAY_DATA, 0) {

	private var elemType: ArgType = getElementTypeByWidth(elemSize)

	constructor(payload: IArrayPayload) : this(payload.data, payload.size, payload.elementSize)

	val elementType: ArgType get() = elemType

	/** 把原始数组展开成一组字面量参数（供代码生成输出 `{...}`）。 */
	fun getLiteralArgs(type: ArgType): List<LiteralArg> {
		val list = ArrayList<LiteralArg>(size)
		val array = data
		when (elemSize) {
			1 -> for (b in array as ByteArray) {
				list.add(InsnArg.lit(b.toLong(), type))
			}

			2 -> for (b in array as ShortArray) {
				list.add(InsnArg.lit(b.toLong(), type))
			}

			4 -> for (b in array as IntArray) {
				list.add(InsnArg.lit(b.toLong(), type))
			}

			8 -> for (b in array as LongArray) {
				list.add(InsnArg.lit(b, type))
			}

			else -> throw KadxRuntimeException("Unknown type: " + data?.javaClass + ", expected: " + type)
		}
		return list
	}

	override fun isSame(obj: InsnNode): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is FillArrayData || !super.isSame(obj)) {
			return false
		}
		// data 是原始数组对象，原 Java 用引用比较
		return elemType == obj.elemType && data === obj.data
	}

	override fun copy(): InsnNode {
		val copy = FillArrayData(data, size, elemSize)
		copy.elemType = elemType
		return copyCommonParams(copy)
	}

	fun dataToString(): String = when (elemSize) {
		1 -> Arrays.toString(data as ByteArray?)
		2 -> Arrays.toString(data as ShortArray?)
		4 -> Arrays.toString(data as IntArray?)
		8 -> Arrays.toString(data as LongArray?)
		else -> "?"
	}

	override fun toString(): String = super.toString() + ", data: " + dataToString()

	companion object {
		// 各字节宽度对应的“未知但限定范围”的元素类型
		private val ONE_BYTE_TYPE = ArgType.unknown(PrimitiveType.BYTE, PrimitiveType.BOOLEAN)
		private val TWO_BYTES_TYPE = ArgType.unknown(PrimitiveType.SHORT, PrimitiveType.CHAR)
		private val FOUR_BYTES_TYPE = ArgType.unknown(PrimitiveType.INT, PrimitiveType.FLOAT)
		private val EIGHT_BYTES_TYPE = ArgType.unknown(PrimitiveType.LONG, PrimitiveType.DOUBLE)

		private fun getElementTypeByWidth(elementWidthUnit: Int): ArgType = when (elementWidthUnit) {
			0, 1 -> ONE_BYTE_TYPE
			2 -> TWO_BYTES_TYPE
			4 -> FOUR_BYTES_TYPE
			8 -> EIGHT_BYTES_TYPE
			else -> throw KadxRuntimeException("Unknown array element width: $elementWidthUnit")
		}
	}
}
