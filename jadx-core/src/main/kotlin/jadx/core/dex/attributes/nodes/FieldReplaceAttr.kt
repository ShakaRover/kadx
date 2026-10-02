package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.PinnedAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.instructions.args.InsnArg

/**
 * 字段替换属性：把对某个字段的访问替换为其它表达式。
 *
 * **两种替换来源**（[ReplaceWith]）：
 * - [ReplaceWith.CLASS_INSTANCE]：替换为一个类实例引用（如 `Foo.class`）；
 * - [ReplaceWith.VAR]：替换为一个寄存器/变量引用。
 *
 * **Kotlin 转换说明**：原 Java 有两个重载构造器；这里用私有主构造器 + 两个次构造器
 * 保持相同的 JVM 构造器签名，[replaceObj] 用 `Any` 承载，取值时再转型。
 */
class FieldReplaceAttr private constructor(
	val replaceType: ReplaceWith,
	private val replaceObj: Any,
) : PinnedAttribute() {

	/** 替换类型 */
	enum class ReplaceWith {
		CLASS_INSTANCE,
		VAR,
	}

	constructor(cls: ClassInfo) : this(ReplaceWith.CLASS_INSTANCE, cls)

	constructor(reg: InsnArg) : this(ReplaceWith.VAR, reg)

	/** 取类实例引用（仅当 [replaceType] 为 CLASS_INSTANCE 时有效） */
	fun getClsRef(): ClassInfo = replaceObj as ClassInfo

	/** 取变量引用（仅当 [replaceType] 为 VAR 时有效） */
	fun getVarRef(): InsnArg = replaceObj as InsnArg

	override fun getAttrType(): AType<FieldReplaceAttr> = AType.FIELD_REPLACE

	override fun toString(): String = "REPLACE: $replaceType $replaceObj"
}
