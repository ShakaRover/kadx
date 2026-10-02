package jadx.api.deobf.impl

import jadx.api.deobf.IDeobfCondition
import jadx.api.deobf.IDeobfCondition.Action
import jadx.api.deobf.IRenameCondition
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode

/**
 * 把多条 [IDeobfCondition] 合并成一个 [IRenameCondition]。
 *
 * **合并规则**（按列表顺序逐条询问，先命中先决定）：
 * - `NO_ACTION`：跳过，继续问下一条；
 * - `FORCE_RENAME`：立即返回 true（改名）；
 * - `FORBID_RENAME`：立即返回 false（禁止改名）；
 * - 全部都不干预：返回 false。
 *
 * **Kotlin 转换说明**：两个静态工厂方法放入 `companion object` 并加 `@JvmStatic`，
 * Java 侧 `CombineDeobfConditions.combine(...)` 调用不变。构造器私有，只能经工厂创建。
 */
class CombineDeobfConditions private constructor(
	private val conditions: List<IDeobfCondition>,
) : IRenameCondition {

	companion object {
		/** 用条件列表组合（列表不可为空）。 */
		@JvmStatic
		fun combine(conditions: List<IDeobfCondition>): IRenameCondition = CombineDeobfConditions(conditions)

		/** 用可变参数组合，等价于 `combine(Arrays.asList(conditions))`。 */
		@JvmStatic
		fun combine(vararg conditions: IDeobfCondition): IRenameCondition = CombineDeobfConditions(conditions.toList())
	}

	init {
		if (conditions.isEmpty()) {
			throw IllegalArgumentException("Conditions list can't be empty")
		}
	}

	/**
	 * 依次对每个条件执行 [check]，按上述规则返回最终结果。
	 * 参数是一个“条件 -> 动作”的取值函数（对应原 Java 的 `Function`）。
	 */
	private fun combineFunc(check: (IDeobfCondition) -> Action): Boolean {
		for (c in conditions) {
			when (check(c)) {
				Action.NO_ACTION -> {
					// 忽略，继续检查下一个条件
				}

				Action.FORCE_RENAME -> return true

				Action.FORBID_RENAME -> return false
			}
		}
		return false
	}

	override fun init(root: RootNode) {
		conditions.forEach { c -> c.init(root) }
	}

	override fun shouldRename(pkg: PackageNode): Boolean = combineFunc { c -> c.check(pkg) }

	override fun shouldRename(cls: ClassNode): Boolean = combineFunc { c -> c.check(cls) }

	override fun shouldRename(fld: FieldNode): Boolean = combineFunc { c -> c.check(fld) }

	override fun shouldRename(mth: MethodNode): Boolean = combineFunc { c -> c.check(mth) }
}
