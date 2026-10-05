package kadx.core.dex.attributes.nodes

import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.instructions.args.ArgType
import java.util.Collections

/**
 * 类的类型变量（泛型参数）属性。
 *
 * **存什么**：
 * - [typeVars]：当前类自身声明的类型变量（如 `class Foo<T, U>` 中的 T、U）；
 * - [superTypeMaps]：父类/接口的类型变量映射，key 为父类型原始对象名，
 *   value 为“父类型变量 → 本类变量”的映射，用于替换继承来的泛型。
 *
 * **Kotlin 转换说明**：静态常量 [EMPTY] 放入 companion object。
 */
class ClassTypeVarsAttr(
	val typeVars: List<ArgType>,
	private val superTypeMaps: Map<String, Map<ArgType, ArgType>>,
) : IKadxAttribute {

	companion object {
		/** 空的类型变量属性（无泛型时共享使用） */
		val EMPTY: ClassTypeVarsAttr = ClassTypeVarsAttr(Collections.emptyList(), Collections.emptyMap())
	}

	/** 查询指定父类型对应的类型变量映射；没有映射时返回空表 */
	fun getTypeVarsMapFor(type: ArgType): Map<ArgType, ArgType> = superTypeMaps[type.getObject()] ?: Collections.emptyMap()

	override val attrType: AType<ClassTypeVarsAttr> get() = AType.CLASS_TYPE_VARS

	override fun toString(): String = "ClassTypeVarsAttr{$typeVars, super maps: $superTypeMaps}"
}
