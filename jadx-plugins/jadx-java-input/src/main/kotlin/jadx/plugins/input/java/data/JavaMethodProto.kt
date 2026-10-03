package jadx.plugins.input.java.data

import jadx.api.plugins.input.data.IMethodProto
import jadx.api.plugins.utils.Utils

/**
 * class 文件方法原型（返回类型 + 参数描述符列表）。
 *
 **做什么**：由 [DescriptorParser] 从描述符填充；[JavaMethodRef.reset] 会清空以便复用。
 *
 **为什么 backing 字段改名且为 protected**：接口 getter 需要显式 override fun（Kotlin 属性
 * 不会自动实现 Kotlin 接口的抽象函数），同名属性会产生 platform declaration clash；
 * protected 让子类 [JavaMethodRef.load] 能直接检查"是否已加载"而不经过会 NPE 的 getter。
 */
open class JavaMethodProto : IMethodProto {

	protected var retType: String? = null
	protected var argTypesList: List<String?>? = null

	// 接口声明非空；未 load 前实际为 null，调用方解引用时与原 Java 一样 NPE
	override val returnType: String get() = retType ?: throw NullPointerException("retType is null")

	fun setReturnType(returnType: String?) {
		retType = returnType
	}

	// argTypesList 元素理论上可空（损坏 class），透传给声明非空的接口类型（擦除后等价）
	override val argTypes: List<String> get() {
		@Suppress("UNCHECKED_CAST")
		return checkNotNull(argTypesList) as List<String>
	}

	fun setArgTypes(argTypes: List<String?>?) {
		argTypesList = argTypes
	}

	override fun toString(): String = "(" + Utils.listToStr(argTypesList) + ")" + retType
}
