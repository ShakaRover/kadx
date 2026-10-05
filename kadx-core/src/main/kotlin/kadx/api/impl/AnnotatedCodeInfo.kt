package kadx.api.impl

import kadx.api.ICodeInfo
import kadx.api.metadata.ICodeAnnotation
import kadx.api.metadata.ICodeMetadata
import kadx.api.metadata.impl.CodeMetadataStorage

/**
 * 带元数据的 [ICodeInfo] 实现：代码文本 + 行号映射 + 注解映射。
 *
 * **做什么**：构造时把原始 `Map` 交给 [CodeMetadataStorage.build] 建成可快速反向查找的
 * 元数据存储；[hasMetadata] 通过引用比较判断是否为空元数据（与原 Java `!=` 等价）。
 *
 * 公共 API；属性在 JVM 上仍生成同名 getter。
 */
class AnnotatedCodeInfo(
	code: String,
	lineMapping: Map<Int, Int>,
	annotations: Map<Int, ICodeAnnotation>,
) : ICodeInfo {

	private val code: String = code
	private val metadata: ICodeMetadata = CodeMetadataStorage.build(lineMapping, annotations)

	override val codeStr: String get() = code

	override val codeMetadata: ICodeMetadata get() = metadata

	// 原 Java 用引用比较 metadata != ICodeMetadata.EMPTY，Kotlin 对应 `!==`
	override fun hasMetadata(): Boolean = metadata !== ICodeMetadata.EMPTY

	override fun toString(): String = code
}
