package jadx.core.deobf.conditions

import jadx.api.deobf.IDeobfCondition.Action
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode

/**
 * 排除 Android 资源类 `R` 及其内部类的重命名条件。
 *
 * **背景**：Android 的 `R` 类（如 `com.example.R`）及其内部类（`R.string`、`R.drawable` …）
 * 只是资源 ID 的容器，重命名它们会破坏与资源表的对应关系，因此必须保护。
 *
 * **识别规则**（与原始实现一致）：
 * 1. 类上已经打了 [AFlag.ANDROID_R_CLASS] 标记，直接认定；
 * 2. 短名为 `R`；
 * 3. `R` 自身没有方法、没有字段；
 * 4. 所有内部类的方法只能是构造器或 `<clinit>`；
 * 5. 所有字段类型必须是 `int` 或 `int[]`。
 *
 * 命中后会给类打上 [AFlag.ANDROID_R_CLASS] 标记，方便后续快速判断。
 */
class ExcludeAndroidRClass : AbstractDeobfCondition() {

	override fun check(cls: ClassNode): Action {
		if (isR(cls.getTopParentClass())) {
			return Action.FORBID_RENAME
		}
		return Action.NO_ACTION
	}

	private fun isR(cls: ClassNode): Boolean {
		if (cls.contains(AFlag.ANDROID_R_CLASS)) {
			return true
		}
		if (cls.classInfo.shortName != "R") {
			return false
		}
		if (cls.methods.isNotEmpty() || cls.fields.isNotEmpty()) {
			return false
		}
		for (inner in cls.innerClasses) {
			for (m in inner.methods) {
				if (!m.getMethodInfo().isConstructor() && !m.getMethodInfo().isClassInit()) {
					return false
				}
			}
			// 注意：原实现这里遍历的是外层 cls 的字段（而非 inner），为保持语义一致原样保留
			for (field in cls.fields) {
				val type = field.type
				if (type !== ArgType.INT && (!type.isArray() || type.getArrayElement() !== ArgType.INT)) {
					return false
				}
			}
		}
		cls.add(AFlag.ANDROID_R_CLASS)
		return true
	}
}
