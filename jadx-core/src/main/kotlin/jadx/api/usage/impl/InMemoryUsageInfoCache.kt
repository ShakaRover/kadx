package jadx.api.usage.impl

import jadx.api.usage.IUsageInfoCache
import jadx.api.usage.IUsageInfoData
import jadx.core.dex.nodes.RootNode

/**
 * 内存中的使用信息缓存：把 usage 数据挂在内存里，供同一次反编译会话复用。
 *
 * **做什么**：`get` 时校验当前 root 节点是否与缓存时一致，一致才返回数据。
 *
 * **为什么这样写**：`data` 字段与某个 root 节点实例绑定，保存其 `hashCode` 作为标识；
 * root 变化时（新的一次加载）自动失效，避免把旧数据套到新树上。
 */
class InMemoryUsageInfoCache : IUsageInfoCache {

	/** 缓存的数据；未缓存时为 `null`（原 Java 字段同样允许为 null）。 */
	private var data: IUsageInfoData? = null

	/**
	 * `data` 字段绑定的 root 节点实例，保存其 hash，root 变化时据此重置缓存。
	 */
	private var rootNodeHash = 0

	override fun get(root: RootNode): IUsageInfoData? = if (rootNodeHash == root.hashCode()) data else null

	override fun set(root: RootNode, data: IUsageInfoData) {
		this.rootNodeHash = root.hashCode()
		this.data = data
	}

	override fun close() {
		this.rootNodeHash = 0
		this.data = null
	}
}
