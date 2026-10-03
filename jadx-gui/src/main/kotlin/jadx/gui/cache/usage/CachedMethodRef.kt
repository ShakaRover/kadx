package jadx.gui.cache.usage

import jadx.api.plugins.input.data.IMethodRef
import jadx.core.codegen.TypeGen
import jadx.core.dex.info.MethodInfo

/**
 * 可缓存的（不可解析）方法引用。
 *
 * **做什么**：usage 数据中有些被调用的方法在当前工程里无法解析，无法用
 * [jadx.core.dex.nodes.MethodNode] 表示；本类用字符串描述其签名
 * （父类类型、方法名、返回类型、参数类型），并实现 [IMethodRef] 以便复用
 * `MethodInfo.fromRef` 的解析逻辑。
 *
 * **为什么显式写 `getXxx()`**：[IMethodRef] 是接口，Kotlin 属性不会自动实现接口方法，
 * 必须保留显式函数形态，Java / Kotlin 调用方都零改动。
 */
class CachedMethodRef : IMethodRef {

	private var parentClassType: String
	private var name: String
	private var returnType: String
	private var argTypes: List<String>

	constructor(parentClassType: String, name: String, returnType: String, argTypes: List<String>) {
		this.parentClassType = parentClassType
		this.name = name
		this.returnType = returnType
		this.argTypes = argTypes
	}

	/** 由 [MethodInfo] 的签名构造（参数为原始类型描述符，与 [TypeGen] 输出一致）。 */
	constructor(mthInfo: MethodInfo) : this(
		TypeGen.signature(mthInfo.declClass.type),
		mthInfo.name,
		TypeGen.signature(mthInfo.returnType),
		TypeGen.signatures(mthInfo.argumentsTypes),
	)

	override fun getParentClassType(): String = parentClassType

	fun setParentClassType(parentClassType: String) {
		this.parentClassType = parentClassType
	}

	override fun getName(): String = name

	fun setName(name: String) {
		this.name = name
	}

	override fun getReturnType(): String = returnType

	fun setReturnType(returnType: String) {
		this.returnType = returnType
	}

	override fun getArgTypes(): List<String> = argTypes

	fun setArgTypes(argTypes: List<String>) {
		this.argTypes = argTypes
	}

	/** 无唯一 id，表示不参与输入层的引用缓存。 */
	override fun getUniqId(): Int = 0

	/** 已是完整签名，无需惰性加载。 */
	override fun load() {}
}
