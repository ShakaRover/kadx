package jadx.api.impl

import jadx.api.ICodeInfo
import jadx.api.metadata.ICodeMetadata

/**
 * 只有代码文本、没有元数据的 [ICodeInfo] 实现。
 *
 * **做什么**：当不需要代码位置映射时（如 JSON 导出、异常堆栈输出）使用，
 * [getCodeMetadata] 恒返回空元数据，[hasMetadata] 恒为 false。
 *
 * 公共 API；getter 保留显式函数形态，Java 调用方零改动。
 */
class SimpleCodeInfo(private val code: String) : ICodeInfo {

	override fun getCodeStr(): String = code

	override fun getCodeMetadata(): ICodeMetadata = ICodeMetadata.EMPTY

	override fun hasMetadata(): Boolean = false

	override fun toString(): String = code
}
