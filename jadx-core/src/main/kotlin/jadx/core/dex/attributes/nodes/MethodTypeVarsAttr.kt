package jadx.core.dex.attributes.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.utils.Utils

/**
 * 方法类型变量属性：记录当前方法作用域内已知的泛型类型变量（type variables）。
 *
 * **为什么要有空实例？** 很多方法没有任何泛型变量，[build] 对空集合返回共享的 [EMPTY]
 * 单例，避免反复创建属性对象；[toString] 通过身份比较识别该哨兵。
 *
 * **Kotlin 转换说明**：
 * - 原 Java 静态方法 `build` → companion + `@JvmStatic`，Java 调用方写法不变；
 * - 原 Java `this == EMPTY` 是对象引用比较，Kotlin 必须写 `this === EMPTY`；
 * - 构造器私有，保持单例语义。
 */
class MethodTypeVarsAttr private constructor(val typeVars: Set<ArgType>) : IJadxAttribute {

	companion object {
		/** 共享空实例：表示“没有类型变量” */
		private val EMPTY = MethodTypeVarsAttr(emptySet())

		/** 构建属性；[typeVars] 为空时返回共享的 [EMPTY] 单例 */
		fun build(typeVars: Set<ArgType>): MethodTypeVarsAttr {
			if (Utils.isEmpty(typeVars)) {
				return EMPTY
			}
			return MethodTypeVarsAttr(typeVars)
		}
	}

	/** 方法作用域内已知的类型变量集合 */

	override val attrType: AType<MethodTypeVarsAttr> get() = AType.METHOD_TYPE_VARS

	override fun toString(): String {
		if (this === EMPTY) {
			return "TYPE_VARS: EMPTY"
		}
		return "TYPE_VARS: $typeVars"
	}
}
