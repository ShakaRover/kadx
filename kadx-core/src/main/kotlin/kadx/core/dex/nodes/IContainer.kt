package kadx.core.dex.nodes

import kadx.api.ICodeWriter
import kadx.core.codegen.RegionGen
import kadx.core.dex.attributes.IAttributeNode
import kadx.core.utils.exceptions.CodegenException

interface IContainer : IAttributeNode {
	/**
	 * Unique id for use in 'toString()' method
	 */
	fun baseString(): String

	/**
	 * 分发到 RegionGen 中对应的 generate 方法。
	 * 保留 @Throws 是为了让 Java 实现类可以声明 `throws CodegenException`（Kotlin 默认不写受检异常）。
	 */
	@Throws(CodegenException::class)
	fun generate(regionGen: RegionGen, code: ICodeWriter): Unit = throw CodegenException("Code generate not implemented for container: ${javaClass.simpleName}")
}
