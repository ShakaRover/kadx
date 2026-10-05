package kadx.core.dex.visitors.typeinference

/**
 * 类型边界（bound）的类别。
 *
 * 在类型推导里，每个 SSA 变量会被若干“边界”约束：
 * - [ASSIGN]：变量被赋值时得到的类型（结果 ← 源，如 `x = ...`）；
 * - [USE]：变量被使用处要求的类型（源 → 使用点，如作为方法参数/字段值）。
 *
 * 推导算法会在这些边界之间取交集/最窄类型，本枚举用于区分约束方向。
 */
enum class BoundEnum {
	ASSIGN,
	USE,
}
