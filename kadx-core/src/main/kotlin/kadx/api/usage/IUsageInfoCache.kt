package kadx.api.usage

import kadx.core.dex.nodes.RootNode
import java.io.Closeable

/**
 * 使用信息（usage info）缓存接口。
 *
 * **做什么**：usage 分析的结果（类依赖、字段/方法被谁使用等）可能很大，重新计算代价高；
 * 本接口抽象出“按 root 节点缓存 / 读取 / 关闭”的能力，默认实现见
 * `kadx.api.usage.impl.InMemoryUsageInfoCache`，磁盘缓存实现见 GUI 模块。
 *
 * **为什么保持接口方法形态**：本接口由 kadx-cli / kadx-gui / 插件（Java）实现，
 * 方法名必须与原 Java 完全一致，故保留显式 `get` / `set` 函数，Java 实现方零改动。
 */
interface IUsageInfoCache : Closeable {

	/** 取出 [root] 对应的使用信息；未缓存时返回 `null`（原 Java 允许返回 null）。 */
	fun get(root: RootNode): IUsageInfoData?

	/** 缓存 [root] 对应的使用信息。 */
	fun set(root: RootNode, data: IUsageInfoData)
}
