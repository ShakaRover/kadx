package jadx.api.plugins.resources

import jadx.api.ResourceFile
import jadx.core.dex.nodes.RootNode
import jadx.core.xmlgen.ResContainer
import java.io.IOException
import java.io.InputStream

/**
 * [ResContainer] 工厂：用于支持不同格式的资源内容解析。
 *
 * **用法**：插件通过 `context.getResourcesLoader().addResContainerFactory()` 注册，
 * 从而为自定义格式的文件提供内容解析。
 *
 * **为什么保持 Java 可实现**：[init] 是可选默认方法，[create] 是唯一抽象方法，
 * 因此 Java 侧可用 lambda / 匿名类实现；保留 [Throws] 以维持受检异常声明。
 */
interface IResContainerFactory {

	/**
	 * 可选的初始化方法。
	 */
	fun init(root: RootNode) {
		// 默认空实现，插件可按需覆写
	}

	/**
	 * 检查资源文件是否为预期格式，并尝试解析其内容。
	 *
	 * @return 格式匹配时返回 [ResContainer]，否则返回 `null`。
	 */
	@Throws(IOException::class)
	fun create(resFile: ResourceFile, inputStream: InputStream): ResContainer?
}
