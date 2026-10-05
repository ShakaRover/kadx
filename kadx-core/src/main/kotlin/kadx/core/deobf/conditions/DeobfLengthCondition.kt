package kadx.core.deobf.conditions

import kadx.api.deobf.IDeobfCondition
import kadx.api.deobf.IDeobfCondition.Action
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.PackageNode
import kadx.core.dex.nodes.RootNode

/**
 * 按名字长度决定是否重命名的条件。
 *
 * **背景**：被混淆的短名（如 `a`、`b`）或异常的长名通常没有语义，应该被替换成
 * 更有意义的别名；而长度在 `[minLength, maxLength]` 区间内的名字认为是“可读的”，
 * 保留不动。
 *
 * 长度范围来自 [kadx.api.KadxArgs] 的配置（`--deobf-min` / `--deobf-max`）。
 */
class DeobfLengthCondition : IDeobfCondition {

	private var minLength = 0
	private var maxLength = 0

	override fun init(root: RootNode) {
		val args = root.args
		this.minLength = args.deobfuscationMinLength
		this.maxLength = args.deobfuscationMaxLength
	}

	/** 名字长度不在允许区间内 → 强制重命名。 */
	private fun checkName(s: String): Action {
		val len = s.length
		if (len < minLength || len > maxLength) {
			return Action.FORCE_RENAME
		}
		return Action.NO_ACTION
	}

	override fun check(pkg: PackageNode): Action = checkName(pkg.name)

	override fun check(cls: ClassNode): Action = checkName(cls.name)

	override fun check(fld: FieldNode): Action = checkName(fld.name)

	override fun check(mth: MethodNode): Action = checkName(mth.name)
}
