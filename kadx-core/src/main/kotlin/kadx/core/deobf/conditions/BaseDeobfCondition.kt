package kadx.core.deobf.conditions

import kadx.api.deobf.IDeobfCondition.Action
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.PackageNode

/**
 * 基础重命名条件：禁止对以下节点做反混淆重命名。
 *
 * - 带有 [AFlag.DONT_RENAME] 标记的节点（通常由用户或其它 pass 显式保护）；
 * - **已经有别名**的节点（说明已经被重命名过，不能再次覆盖，避免名字被反复改写）。
 *
 * **为什么要有它**：这是所有条件里优先级最高的一条“兜底规则”，保证已经确定的
 * 名字不会被后续流程破坏。
 */
class BaseDeobfCondition : AbstractDeobfCondition() {

	override fun check(pkg: PackageNode): Action {
		if (pkg.contains(AFlag.DONT_RENAME) || pkg.hasAlias()) {
			return Action.FORBID_RENAME
		}
		return Action.NO_ACTION
	}

	override fun check(cls: ClassNode): Action {
		if (cls.contains(AFlag.DONT_RENAME) || cls.classInfo.hasAlias()) {
			return Action.FORBID_RENAME
		}
		return Action.NO_ACTION
	}

	override fun check(mth: MethodNode): Action {
		if (mth.contains(AFlag.DONT_RENAME) ||
			mth.methodInfo.hasAlias() ||
			mth.isConstructor()
		) {
			return Action.FORBID_RENAME
		}
		return Action.NO_ACTION
	}

	override fun check(fld: FieldNode): Action {
		if (fld.contains(AFlag.DONT_RENAME) || fld.getFieldInfo().hasAlias()) {
			return Action.FORBID_RENAME
		}
		return Action.NO_ACTION
	}
}
