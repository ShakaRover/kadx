package jadx.core.deobf.conditions

import jadx.api.deobf.IDeobfCondition
import jadx.api.deobf.IDeobfCondition.Action
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode

/**
 * 反混淆重命名条件（DeobfCondition）的抽象基类。
 *
 * **用途**：jadx 在反混淆（deobfuscation）时，会依次询问若干“条件”是否允许重命名某个
 * 包 / 类 / 字段 / 方法。本基类为所有条件提供**默认放行**的实现：默认返回
 * [Action.NO_ACTION]（不干预），子类只需覆写自己关心的那一个 `check` 重载即可。
 *
 * **为什么需要它**：避免每个条件都实现 4 个 `check` 方法，减少样板代码。
 */
abstract class AbstractDeobfCondition : IDeobfCondition {

	/** 条件初始化：默认什么都不做，子类可按需预扫描整棵 dex 树。 */
	override fun init(root: RootNode) {
	}

	/** 对包节点默认不干预。 */
	override fun check(pkg: PackageNode): Action = Action.NO_ACTION

	/** 对类节点默认不干预。 */
	override fun check(cls: ClassNode): Action = Action.NO_ACTION

	/** 对字段节点默认不干预。 */
	override fun check(fld: FieldNode): Action = Action.NO_ACTION

	/** 对方法节点默认不干预。 */
	override fun check(mth: MethodNode): Action = Action.NO_ACTION
}
