package jadx.core.dex.visitors.methods

import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.IMethodDetails

/**
 * 可变的 [IMethodDetails] 包装：以某个已有实现为基底，允许修改返回类型、
 * 参数类型、泛型参数、异常类型、变长标志与访问标志。
 *
 * **用途**：方法调用重载解析时，需要临时替换/修正某个方法签名的类型信息
 * （例如把未知类型换成推导出的类型），但又不能改动原始 AST 节点，因此拷贝一份。
 *
 * **Kotlin 转换说明**：原 Java 用 `Collections.unmodifiableList` 包一层，
 * Kotlin 侧只读的 `List` 类型已足够表达“不可改”的意图，故直接持有；
 * 实现接口的 getter 保持显式 `override fun`（接口方法名与属性名不同）。
 */
class MutableMethodDetails(base: IMethodDetails) : IMethodDetails {

	private val mthInfo: MethodInfo = base.getMethodInfo()
	private var retType: ArgType = base.getReturnType()
	private var argTypes: List<ArgType> = base.getArgTypes()
	private var typeParams: List<ArgType> = base.getTypeParameters()
	private var throwTypes: List<ArgType> = base.getThrows()
	private var varArg: Boolean = base.isVarArg()
	private var accFlags: Int = base.getRawAccessFlags()

	override fun getMethodInfo(): MethodInfo = mthInfo

	override fun getReturnType(): ArgType = retType

	override fun getArgTypes(): List<ArgType> = argTypes

	override fun getTypeParameters(): List<ArgType> = typeParams

	override fun getThrows(): List<ArgType> = throwTypes

	override fun isVarArg(): Boolean = varArg

	fun setRetType(retType: ArgType) {
		this.retType = retType
	}

	fun setArgTypes(argTypes: List<ArgType>) {
		this.argTypes = argTypes
	}

	fun setTypeParams(typeParams: List<ArgType>) {
		this.typeParams = typeParams
	}

	fun setThrowTypes(throwTypes: List<ArgType>) {
		this.throwTypes = throwTypes
	}

	fun setVarArg(varArg: Boolean) {
		this.varArg = varArg
	}

	override fun getRawAccessFlags(): Int = accFlags

	fun setRawAccessFlags(accFlags: Int) {
		this.accFlags = accFlags
	}

	override fun toAttrString(): String = super<IMethodDetails>.toAttrString() + " (mut)"

	override fun toString(): String = "Mutable" + toAttrString()
}
