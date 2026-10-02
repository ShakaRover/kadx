package jadx.api.impl

import jadx.api.ICodeCache
import jadx.api.ICodeInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * 内存代码缓存：用 [ConcurrentHashMap] 保存“类全名 -> 代码信息”。
 *
 * **做什么**：默认缓存实现；[get] 找不到时返回 [ICodeInfo.EMPTY]（而非 null），
 * [getCode] 找不到时返回 null，与原 Java 行为一致。
 *
 * **为什么用 ConcurrentHashMap**：反编译可能多线程并发访问，需要线程安全。
 */
class InMemoryCodeCache : ICodeCache {

	private val storage: MutableMap<String, ICodeInfo> = ConcurrentHashMap()

	override fun add(clsFullName: String, codeInfo: ICodeInfo) {
		storage[clsFullName] = codeInfo
	}

	override fun remove(clsFullName: String) {
		storage.remove(clsFullName)
	}

	override fun get(clsFullName: String): ICodeInfo = storage[clsFullName] ?: ICodeInfo.EMPTY

	override fun getCode(clsFullName: String): String? = storage[clsFullName]?.getCodeStr()

	override fun contains(clsFullName: String): Boolean = storage.containsKey(clsFullName)

	@Throws(IOException::class)
	override fun close() {
		storage.clear()
	}

	override fun toString(): String = "InMemoryCodeCache: size=" + storage.size
}
