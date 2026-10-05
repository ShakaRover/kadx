package kadx.api.deobf

import kadx.api.deobf.impl.CombineDeobfConditions
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.PackageNode
import kadx.core.dex.nodes.RootNode

/**
 * 单个反混淆“判断条件”的接口。
 *
 * **做什么**：把“是否改名”拆成可组合的小条件，每个条件对包 / 类 / 字段 / 方法返回
 * [Action]（不干预 / 强制改名 / 禁止改名）。这样可以把多条规则合并成一个
 * [IRenameCondition] 实例，见 [CombineDeobfConditions.combine]。
 *
 * **Kotlin 转换说明**：`Action` 内嵌枚举保持原名（JVM 名 `IDeobfCondition$Action`），
 * 所有方法保持显式函数形态，Java 实现方零改动。
 */
interface IDeobfCondition {

	/** 条件对某个节点给出的动作。 */
	enum class Action {
		/** 不干预，交给其它条件决定。 */
		NO_ACTION,

		/** 强制改名。 */
		FORCE_RENAME,

		/** 禁止改名。 */
		FORBID_RENAME,
	}

	/** 初始化：反混淆开始前调用一次，可在此预扫描整棵 dex 树。 */
	fun init(root: RootNode)

	/** 检查包节点。 */
	fun check(pkg: PackageNode): Action

	/** 检查类节点。 */
	fun check(cls: ClassNode): Action

	/** 检查字段节点。 */
	fun check(fld: FieldNode): Action

	/** 检查方法节点。 */
	fun check(mth: MethodNode): Action
}
