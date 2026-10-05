package kadx.api

import kadx.core.xmlgen.ResContainer

/**
 * 资源文件实现：内容是一段已生成的代码文本（[ICodeInfo]），包装成文本资源返回。
 *
 * 属于公共 API 的实现细节；decompiler 传 null（由基类 [ResourceFile] 允许）。
 */
class ResourceFileContent(
	name: String,
	type: ResourceType,
	private val content: ICodeInfo,
) : ResourceFile(null, name, type) {

	override fun loadContent(): ResContainer = ResContainer.textResource(getDeobfName(), content)
}
