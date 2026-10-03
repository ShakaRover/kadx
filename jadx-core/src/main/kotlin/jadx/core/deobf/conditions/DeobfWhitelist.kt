package jadx.core.deobf.conditions

import jadx.api.deobf.IDeobfCondition.Action
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.PackageNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils

/**
 * 反混淆白名单条件：白名单里的包 / 类不允许被重命名。
 *
 * **用途**：像 `android.support.v4.*`、`androidx.annotation.Px` 这类框架类，
 * 它们的原始名字就是有意义的，重命名反而会破坏代码可读性甚至编译，因此需要保护。
 *
 * 白名单条目以 `.*` 结尾表示“整个包”，否则表示“精确的类全名”。
 */
class DeobfWhitelist : AbstractDeobfCondition() {

	companion object {
		/** 默认白名单（包用 `.*` 结尾，类用全名） */
		val DEFAULT_LIST: List<String> = listOf(
			"android.support.v4.*",
			"android.support.v7.*",
			"android.support.v4.os.*",
			"android.support.annotation.Px",
			"androidx.core.os.*",
			"androidx.annotation.Px",
		)

		/** 默认白名单的命令行字符串形式（以空格分隔） */
		val DEFAULT_STR: String = Utils.listToString(DEFAULT_LIST, " ")
	}

	/** 需要保护的包全名集合（已去掉末尾的 `.*`） */
	private val packages = HashSet<String>()

	/** 需要保护的类全名集合 */
	private val classes = HashSet<String>()

	override fun init(root: RootNode) {
		packages.clear()
		classes.clear()
		for (whitelistItem in root.args.deobfuscationWhitelist) {
			if (whitelistItem.isNotEmpty()) {
				if (whitelistItem.endsWith(".*")) {
					packages.add(whitelistItem.substring(0, whitelistItem.length - 2))
				} else {
					classes.add(whitelistItem)
				}
			}
		}
	}

	override fun check(pkg: PackageNode): Action {
		if (packages.contains(pkg.getPkgInfo().fullName)) {
			return Action.FORBID_RENAME
		}
		return Action.NO_ACTION
	}

	override fun check(cls: ClassNode): Action {
		if (classes.contains(cls.classInfo.fullName)) {
			return Action.FORBID_RENAME
		}
		return Action.NO_ACTION
	}
}
