package jadx.core.deobf.conditions

import jadx.api.deobf.IDeobfCondition.Action
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode

/**
 * 避免“类名”和“包名”发生冲突的重命名条件。
 *
 * **背景**：反混淆后包名可能被改成 `p000`，类名可能被改成 `C0001`。如果某个类的别名
 * 恰好等于某个包的别名，那么在 Java 源码里就会出现歧义（无法区分是包还是类）。
 *
 * **做法**：初始化时收集所有**包名**到 [avoidClsNames]；检查类时，如果类别名命中这个
 * 集合，就强制重命名（[Action.FORCE_RENAME]），从而换一个不冲突的名字。
 */
class AvoidClsAndPkgNamesCollision : AbstractDeobfCondition() {

	/** 所有包名（别名），类别名不能与之相同 */
	private val avoidClsNames = HashSet<String>()

	override fun init(root: RootNode) {
		avoidClsNames.clear()
		for (pkg in root.getPackages()) {
			avoidClsNames.add(pkg.getName())
		}
	}

	override fun check(cls: ClassNode): Action {
		if (avoidClsNames.contains(cls.alias)) {
			return Action.FORCE_RENAME
		}
		return Action.NO_ACTION
	}
}
