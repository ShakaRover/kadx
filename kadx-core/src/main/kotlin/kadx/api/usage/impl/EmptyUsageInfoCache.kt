package kadx.api.usage.impl

import kadx.api.usage.IUsageInfoCache
import kadx.api.usage.IUsageInfoData
import kadx.core.dex.nodes.RootNode
import java.io.IOException

/**
 * 空的使用信息缓存：什么都不存，每次读取都返回 `null`。
 *
 * **做什么 / 为什么**：当用户不需要重载反编译代码时（例如一次性 CLI 导出），
 * 保留 usage 数据只会白占内存，因此用本实现显式关闭缓存。
 *
 * **Kotlin 转换说明**：`close()` 原 Java 声明了受检 `IOException`，
 * 这里用 `@Throws` 保留，Java 调用方的异常处理逻辑不变。
 */
class EmptyUsageInfoCache : IUsageInfoCache {

	override fun get(root: RootNode): IUsageInfoData? = null

	override fun set(root: RootNode, data: IUsageInfoData) {
	}

	@Throws(IOException::class)
	override fun close() {
	}
}
