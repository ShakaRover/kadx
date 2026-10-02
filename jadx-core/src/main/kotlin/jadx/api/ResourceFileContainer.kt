package jadx.api

import jadx.core.xmlgen.ResContainer

/**
 * 资源文件实现：内容已经由一个 [ResContainer] 提供，直接返回即可。
 *
 * 属于公共 API 的实现细节；decompiler 传 null（由基类 [ResourceFile] 允许）。
 */
class ResourceFileContainer(
	name: String,
	type: ResourceType,
	private val container: ResContainer,
) : ResourceFile(null, name, type) {

	override fun loadContent(): ResContainer = container
}
