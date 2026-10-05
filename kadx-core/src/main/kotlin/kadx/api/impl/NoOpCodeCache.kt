package kadx.api.impl

import kadx.api.ICodeCache
import kadx.api.ICodeInfo

/**
 * 空实现代码缓存：所有写操作忽略，[get] 返回 [ICodeInfo.EMPTY]，[getCode] 返回 null。
 *
 * **做什么**：当用户不需要缓存代码（例如 CLI 只输出到磁盘）时使用，避免占用内存。
 *
 * **为什么提供 [INSTANCE]**：原 Java 是 `public static final` 单例字段，
 * 用 `companion object` + `@JvmField` 保持 Java 侧 `NoOpCodeCache.INSTANCE` 写法不变。
 */
class NoOpCodeCache : ICodeCache {

	companion object {
		@JvmField
		val INSTANCE: NoOpCodeCache = NoOpCodeCache()
	}

	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		// 无操作
	}

	override fun remove(clsFullName: String) {
		// 无操作
	}

	override fun get(clsFullName: String): ICodeInfo = ICodeInfo.EMPTY

	override fun getCode(clsFullName: String): String? = null

	override fun contains(clsFullName: String): Boolean = false

	override fun close() {
		// 无操作
	}

	override fun toString(): String = "NoOpCodeCache"
}
