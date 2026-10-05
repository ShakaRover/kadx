package kadx.api.data

/**
 * 方法内代码元素的引用（方法参数 / 局部变量 / catch / 指令）。
 *
 * **做什么**：用 [getAttachType] 区分目标类别，用 [getIndex] 表示该类别的索引
 * （例如指令的字节码偏移，或变量“寄存器号 << 16 | SSA 版本”的打包值）。
 *
 * **默认比较**：按 [getIndex] 升序，与原 Java 的 `default compareTo` 行为一致。
 */
interface IJavaCodeRef : Comparable<IJavaCodeRef> {

	/** 引用类型。 */
	fun getAttachType(): CodeRefType

	/** 类型相关的索引值。 */
	fun getIndex(): Int

	/** 默认按索引升序比较。 */
	override fun compareTo(other: IJavaCodeRef): Int = getIndex().compareTo(other.getIndex())
}
