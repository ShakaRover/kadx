package jadx.core.dex.visitors.typeinference

import java.util.EnumSet

/**
 * 类型更新行为的标志位集合。
 *
 * **算法意图**：同一套 [TypeUpdate] 逻辑需要按场景微调：
 * - `ALLOW_WIDER`：允许用更宽的类型覆盖当前类型（调试信息、raw 类型回退等）；
 * - `IGNORE_SAME`：即使候选类型与当前类型相同也继续处理；
 * - `IGNORE_UNKNOWN`：遇到无法判断的层级关系时直接拒绝；
 * - `KEEP_GENERICS`：不允许因更宽类型而丢失泛型信息。
 *
 * **Kotlin 转换说明**：静态常量放入 `companion object`，Kotlin 侧写 `TypeUpdateFlags.FLAGS_EMPTY`。
 */
class TypeUpdateFlags private constructor(private val flags: Set<FlagsEnum>) {

	fun isAllowWider(): Boolean = flags.contains(FlagsEnum.ALLOW_WIDER)

	fun isIgnoreSame(): Boolean = flags.contains(FlagsEnum.IGNORE_SAME)

	fun isIgnoreUnknown(): Boolean = flags.contains(FlagsEnum.IGNORE_UNKNOWN)

	fun isKeepGenerics(): Boolean = flags.contains(FlagsEnum.KEEP_GENERICS)

	override fun toString(): String = flags.toString()

	private enum class FlagsEnum {
		ALLOW_WIDER,
		IGNORE_SAME,
		IGNORE_UNKNOWN,
		KEEP_GENERICS,
	}

	companion object {
		val FLAGS_EMPTY: TypeUpdateFlags = build()

		val FLAGS_WIDER: TypeUpdateFlags = build(FlagsEnum.ALLOW_WIDER)

		val FLAGS_WIDER_IGNORE_SAME: TypeUpdateFlags = build(FlagsEnum.ALLOW_WIDER, FlagsEnum.IGNORE_SAME)

		val FLAGS_APPLY_DEBUG: TypeUpdateFlags = build(FlagsEnum.ALLOW_WIDER, FlagsEnum.KEEP_GENERICS, FlagsEnum.IGNORE_UNKNOWN)

		private fun build(vararg flags: FlagsEnum): TypeUpdateFlags {
			val set = if (flags.isEmpty()) {
				EnumSet.noneOf(FlagsEnum::class.java)
			} else {
				EnumSet.copyOf(flags.toList())
			}
			return TypeUpdateFlags(set)
		}
	}
}
