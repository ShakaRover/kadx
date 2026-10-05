package kadx.api.metadata.annotations

import kadx.api.metadata.ICodeAnnotation

/**
 * 类 / 方法体结束标记注解。
 *
 * **做什么**：在生成代码中标记一个类或方法体的结束位置，配合节点声明注解
 * 可以判断某个位置处于哪个节点内部（见 `CodeMetadataStorage.getNodeAt`）。
 *
 * **Kotlin 转换说明**：原 `public static final VALUE` 改为 `companion object` 内
 * `@JvmField val VALUE`，Java 侧 `NodeEnd.VALUE` 与 Kotlin 侧写法都不变。
 * 私有构造器保证全局唯一单例。
 */
class NodeEnd private constructor() : ICodeAnnotation {

	companion object {
		/** 共享单例（比较时请用 `===`）。 */
		@JvmField
		val VALUE: NodeEnd = NodeEnd()
	}

	override val annType: ICodeAnnotation.AnnType get() = ICodeAnnotation.AnnType.END

	override fun toString(): String = "END"
}
