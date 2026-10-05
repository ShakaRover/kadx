package kadx.api.data.impl

import kadx.api.JavaVariable
import kadx.api.data.CodeRefType
import kadx.api.data.IJavaCodeRef
import kadx.api.metadata.annotations.VarNode

/**
 * [IJavaCodeRef] 的默认实现，同时是 Gson 反序列化的目标类。
 *
 * **做什么**：保存“引用类型 + 索引”。索引的含义随类型而变：
 * 指令是字节码偏移，方法参数/变量是打包后的值（见 [forVar]）。
 *
 * **为什么用普通 class**：有自定义 `equals/hashCode`（按类型 + 索引判等），
 * 且需要无参构造器供 Gson 使用；绝不能改成 `data class`。
 *
 * **Kotlin 转换说明**：静态工厂方法放进 `companion object` 并加 `@JvmStatic`，
 * Java 侧 `KadxCodeRef.forInsn(...)` 调用保持不变。
 */
class KadxCodeRef(
	attachType: CodeRefType? = null,
	index: Int = 0,
) : IJavaCodeRef {

	companion object {
		/** 指令引用（按字节码偏移）。 */
		@JvmStatic
		fun forInsn(offset: Int): KadxCodeRef = KadxCodeRef(CodeRefType.INSN, offset)

		/** 方法参数引用（按参数序号）。 */
		@JvmStatic
		fun forMthArg(argIndex: Int): KadxCodeRef = KadxCodeRef(CodeRefType.MTH_ARG, argIndex)

		/**
		 * 局部变量引用：把“寄存器号”和“SSA 版本号”打包成一个 int。
		 *
		 * 高位存寄存器号、低 16 位存 SSA 版本，与原 Java `regNum << 16 | ssaVersion` 等价。
		 */
		@JvmStatic
		fun forVar(regNum: Int, ssaVersion: Int): KadxCodeRef = KadxCodeRef(CodeRefType.VAR, (regNum shl 16) or ssaVersion)

		/** 从 Java 变量视图构造。 */
		@JvmStatic
		fun forVar(javaVariable: JavaVariable): KadxCodeRef = forVar(javaVariable.getReg(), javaVariable.getSsa())

		/** 从变量节点构造。 */
		@JvmStatic
		fun forVar(varNode: VarNode): KadxCodeRef = forVar(varNode.getReg(), varNode.getSsa())

		/** catch 处理器引用（按处理器偏移）。 */
		@JvmStatic
		fun forCatch(handlerOffset: Int): KadxCodeRef = KadxCodeRef(CodeRefType.CATCH, handlerOffset)
	}

	// 私有属性，Gson 反射读写
	private var attachType: CodeRefType? = attachType
	private var index: Int = index

	override fun getAttachType(): CodeRefType = checkNotNull(attachType) { "attachType is not set" }

	fun setAttachType(attachType: CodeRefType) {
		this.attachType = attachType
	}

	override fun getIndex(): Int = index

	fun setIndex(index: Int) {
		this.index = index
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is KadxCodeRef) {
			return false
		}
		return getIndex() == other.getIndex() && getAttachType() === other.getAttachType()
	}

	override fun hashCode(): Int = 31 * getAttachType().hashCode() + getIndex()

	override fun toString(): String = "KadxCodeRef{" +
		"attachType=" + attachType +
		", index=" + index +
		'}'
}
