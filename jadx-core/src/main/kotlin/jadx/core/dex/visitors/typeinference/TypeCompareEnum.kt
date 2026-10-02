package jadx.core.dex.visitors.typeinference

/**
 * 两个类型比较后的结果枚举。
 *
 * **算法意图**：类型推导需要频繁判断“候选类型相对当前类型是更窄、更宽还是冲突”，
 * 本枚举把比较结果细分为普通类型与“泛型差异”两类，便于 [TypeUpdate] 做精细决策：
 * - [EQUAL]：完全相同；
 * - [NARROW]/[WIDER]：第一个类型是第二个的子/父类型（不含泛型差异）；
 * - [NARROW_BY_GENERIC]/[WIDER_BY_GENERIC]：基本类型相同，仅泛型信息有差异；
 * - [CONFLICT]/[CONFLICT_BY_GENERIC]：类型不兼容；
 * - [UNKNOWN]：类型层级信息不足，无法判断。
 *
 * **Kotlin 转换说明**：枚举常量在 JVM 上仍是静态字段，Java 调用方
 * （如 `TypeCompareEnum.CONFLICT`）零改动。
 */
enum class TypeCompareEnum {
	EQUAL,
	NARROW,
	NARROW_BY_GENERIC, // 基本类型相同，但泛型不同
	WIDER,
	WIDER_BY_GENERIC, // 基本类型相同，但泛型不同
	CONFLICT,
	CONFLICT_BY_GENERIC, // 基本类型相同，但泛型冲突
	UNKNOWN,
	;

	/**
	 * 取反：把“第一个相对第二个”的比较结果翻转为“第二个相对第一个”。
	 * 冲突/相等/未知保持自身。
	 */
	fun invert(): TypeCompareEnum = when (this) {
		NARROW -> WIDER
		NARROW_BY_GENERIC -> WIDER_BY_GENERIC
		WIDER -> NARROW
		WIDER_BY_GENERIC -> NARROW_BY_GENERIC
		CONFLICT, CONFLICT_BY_GENERIC, EQUAL, UNKNOWN -> this
	}

	fun isEqual(): Boolean = this == EQUAL

	fun isWider(): Boolean = this == WIDER || this == WIDER_BY_GENERIC

	fun isWiderOrEqual(): Boolean = isEqual() || isWider()

	fun isNarrow(): Boolean = this == NARROW || this == NARROW_BY_GENERIC

	fun isNarrowOrEqual(): Boolean = isEqual() || isNarrow()

	fun isConflict(): Boolean = this == CONFLICT || this == CONFLICT_BY_GENERIC
}
