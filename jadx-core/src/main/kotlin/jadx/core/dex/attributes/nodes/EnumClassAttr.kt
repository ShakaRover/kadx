package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode

/**
 * 枚举类属性：还原后的枚举类所持有的字段与静态初始化方法信息。
 *
 * **用途**：把 DEX 中“静态字段 + 构造器调用”的枚举模式还原成 Java 枚举语法时，
 * 需要知道每个枚举常量的字段、对应构造器指令，以及可选的名称字符串。
 *
 * **Kotlin 转换说明**：
 * - [EnumField.cls] 原 Java 未在构造时赋值，可能为 null，因此声明为可空 `var`；
 * - [staticMethod] 同理可能为 null（尚未设置），声明为可空 `var`。
 */
class EnumClassAttr(val fields: List<EnumField>) : IJadxAttribute {

	/**
	 * 单个枚举常量：字段 + 构造器调用 + 可选名称。
	 *
	 * @param field 枚举常量对应的静态字段
	 * @param constrInsn 初始化该常量时调用的构造器指令
	 * @param nameStr 可选的字符串名称（Dex 中显式给出的名称）
	 */
	class EnumField(
		val field: FieldNode,
		val constrInsn: ConstructorInsn,
		val nameStr: String?,
	) {
		/** 该枚举常量对应的匿名子类（可能为 null，稍后由 setCls 设置） */
		var cls: ClassNode? = null

		override fun toString(): String = "$field($constrInsn) $cls"
	}

	/** 生成枚举常量的静态初始化方法（可能为 null） */
	var staticMethod: MethodNode? = null

	override fun getAttrType(): AType<EnumClassAttr> = AType.ENUM_CLASS

	override fun toString(): String = "Enum fields: $fields"
}
