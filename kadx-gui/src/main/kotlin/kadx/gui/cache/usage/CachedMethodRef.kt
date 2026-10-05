package kadx.gui.cache.usage

import kadx.api.plugins.input.data.IMethodRef
import kadx.core.codegen.TypeGen
import kadx.core.dex.info.MethodInfo

/**
 * 可缓存的（不可解析）方法引用。
 *
 * **做什么**：usage 数据中有些被调用的方法在当前工程里无法解析，无法用
 * [kadx.core.dex.nodes.MethodNode] 表示；本类用字符串描述其签名
 * （父类类型、方法名、返回类型、参数类型），并实现 [IMethodRef] 以便复用
 * `MethodInfo.fromRef` 的解析逻辑。
 */
class CachedMethodRef : IMethodRef {

	private var parentClassTypeValue: String
	private var nameValue: String
	private var returnTypeValue: String
	private var argTypesValue: List<String>

	constructor(parentClassType: String, name: String, returnType: String, argTypes: List<String>) {
		this.parentClassTypeValue = parentClassType
		this.nameValue = name
		this.returnTypeValue = returnType
		this.argTypesValue = argTypes
	}

	/** 由 [MethodInfo] 的签名构造（参数为原始类型描述符，与 [TypeGen] 输出一致）。 */
	constructor(mthInfo: MethodInfo) : this(
		TypeGen.signature(mthInfo.declClass.type),
		mthInfo.name,
		TypeGen.signature(mthInfo.returnType),
		TypeGen.signatures(mthInfo.argumentsTypes),
	)

	override val parentClassType: String get() = parentClassTypeValue

	fun setParentClassType(parentClassType: String) {
		this.parentClassTypeValue = parentClassType
	}

	override val name: String get() = nameValue

	fun setName(name: String) {
		this.nameValue = name
	}

	override val returnType: String get() = returnTypeValue

	fun setReturnType(returnType: String) {
		this.returnTypeValue = returnType
	}

	override val argTypes: List<String> get() = argTypesValue

	fun setArgTypes(argTypes: List<String>) {
		this.argTypesValue = argTypes
	}

	/** 无唯一 id，表示不参与输入层的引用缓存。 */
	override val uniqId: Int get() = 0

	/** 已是完整签名，无需惰性加载。 */
	override fun load() {}
}
