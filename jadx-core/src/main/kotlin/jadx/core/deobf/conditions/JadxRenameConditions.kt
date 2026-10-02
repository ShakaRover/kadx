package jadx.core.deobf.conditions

import jadx.api.deobf.IDeobfCondition
import jadx.api.deobf.IRenameCondition
import jadx.api.deobf.impl.CombineDeobfConditions

/**
 * jadx 默认反混淆条件的组装入口。
 *
 * **用途**：把 jadx 内置的一组 [IDeobfCondition] 组合成一个 [IRenameCondition]，
 * 供 [jadx.api.JadxArgs] 默认使用。
 *
 * 组合顺序（与原实现一致，顺序会影响优先级）：
 * 1. [BaseDeobfCondition] —— 已重命名 / 被标记的节点禁改；
 * 2. [DeobfWhitelist] —— 白名单保护；
 * 3. [ExcludePackageWithTLDNames] —— 顶级域名包保护；
 * 4. [ExcludeAndroidRClass] —— Android `R` 类保护；
 * 5. [AvoidClsAndPkgNamesCollision] —— 避免类名与包名冲突；
 * 6. [DeobfLengthCondition] —— 按名字长度决定是否重命名。
 */
class JadxRenameConditions {

	companion object {
		/**
		 * 返回**可修改**的默认条件列表。
		 * 调用方拿到列表后可以增删条件，再交给
		 * [CombineDeobfConditions.combine] 组合。
		 */
		@JvmStatic
		fun buildDefaultDeobfConditions(): List<IDeobfCondition> {
			val list = ArrayList<IDeobfCondition>()
			list.add(BaseDeobfCondition())
			list.add(DeobfWhitelist())
			list.add(ExcludePackageWithTLDNames())
			list.add(ExcludeAndroidRClass())
			list.add(AvoidClsAndPkgNamesCollision())
			list.add(DeobfLengthCondition())
			return list
		}

		/** 用默认条件列表构建 jadx 默认的 [IRenameCondition]。 */
		@JvmStatic
		fun buildDefault(): IRenameCondition = CombineDeobfConditions.combine(buildDefaultDeobfConditions())
	}
}
