package jadx.api.metadata

/**
 * 可作为代码注解目标的 dex 节点引用（类 / 方法 / 字段 / 包 / 变量）。
 *
 * **做什么**：在 [ICodeAnnotation] 的基础上增加“定义位置”（在反编译代码中的字符偏移），
 * 这样就能把节点声明位置和代码里的注解互相映射。
 *
 * **Kotlin 转换说明**：只读 getter 转为 Kotlin 属性，JVM 上仍生成 `getDefPosition()`，
 * Java 实现方（如 `LineAttrNode` 子类）零改动。
 */
interface ICodeNodeRef : ICodeAnnotation {

	/** 获取定义位置（反编译代码中的字符偏移）。 */
	val defPosition: Int

	/** 设置定义位置。 */
	fun setDefPosition(pos: Int)
}
