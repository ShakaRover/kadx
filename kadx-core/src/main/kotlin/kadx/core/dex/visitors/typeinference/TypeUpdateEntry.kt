package kadx.core.dex.visitors.typeinference

import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg

/**
 * 待应用的“一次类型更新”记录。
 *
 * **算法意图**：[TypeUpdateInfo] 用递增的 [seq] 记录更新先后顺序，
 * 应用时按 seq 排序，保证同一参数被多次建议时以最后一次为准。
 *
 * **Kotlin 转换说明**：保留显式 getter，Java/Kotlin 调用方零改动；
 * [compareTo] 按 [seq] 升序比较。
 */
class TypeUpdateEntry(
	val seq: Int,
	val arg: InsnArg,
	val type: ArgType,
) : Comparable<TypeUpdateEntry> {

	override fun compareTo(other: TypeUpdateEntry): Int = seq.compareTo(other.seq)

	override fun toString(): String = "$type -> ${arg.toShortString()} in ${arg.getParentInsn()}"
}
