package kadx.gui.cache.code

import kadx.api.ICodeCache
import kadx.api.ICodeInfo
import kadx.api.impl.InMemoryCodeCache
import kadx.api.impl.SimpleCodeInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [CodeStringCache] 的 LRU 行为测试（S5-4）。
 *
 * 缓存本身不可见，因此用「回源次数」作为可观察量：条目若仍在字符串缓存里，
 * `getCode` 不会碰 [CodeStringCache] 的 backCache；被淘汰的条目则必须回源。
 */
class CodeStringCacheTest {

	/** 包装一个内存缓存，记录 backCache 被读取了哪些类。 */
	private class CountingCache(private val delegate: ICodeCache = InMemoryCodeCache()) : ICodeCache {
		val reads = ArrayList<String>()

		override fun add(clsFullName: String, codeInfo: ICodeInfo) = delegate.add(clsFullName, codeInfo)

		override fun remove(clsFullName: String) = delegate.remove(clsFullName)

		override fun get(clsFullName: String): ICodeInfo = delegate.get(clsFullName)

		override fun getCode(clsFullName: String): String? {
			reads.add(clsFullName)
			return delegate.getCode(clsFullName)
		}

		override fun contains(clsFullName: String): Boolean = delegate.contains(clsFullName)

		override fun close() = delegate.close()
	}

	@Test
	fun evictsLeastRecentlyUsedEntry() {
		val back = CountingCache()
		val cache = CodeStringCache(back)
		val total = CodeStringCache.CACHE_SIZE + 10
		for (i in 0 until total) {
			cache.add("c$i", SimpleCodeInfo("code$i"))
		}

		// 最早加入的条目应已被淘汰 -> 需要回源
		back.reads.clear()
		assertEquals("code0", cache.getCode("c0"))
		assertTrue(back.reads.contains("c0")) { "evicted entry must be re-read from backCache" }

		// 最近加入的条目应仍在字符串缓存里 -> 不回源
		back.reads.clear()
		assertEquals("code${total - 1}", cache.getCode("c${total - 1}"))
		assertFalse(back.reads.contains("c${total - 1}")) {
			"recent entry must be served from the string cache, reads=${back.reads}"
		}
	}

	@Test
	fun accessRefreshesRecency() {
		val back = CountingCache()
		val cache = CodeStringCache(back)
		val total = CodeStringCache.CACHE_SIZE
		for (i in 0 until total) {
			cache.add("c$i", SimpleCodeInfo("code$i"))
		}
		// 触碰最旧的一条，使它成为最近使用
		cache.getCode("c0")
		// 再加入一条 -> 被淘汰的应是 c1 而不是 c0
		cache.add("new", SimpleCodeInfo("codenew"))

		back.reads.clear()
		assertEquals("code0", cache.getCode("c0"))
		assertFalse(back.reads.contains("c0")) { "c0 was refreshed and must still be cached" }

		back.reads.clear()
		assertEquals("code1", cache.getCode("c1"))
		assertTrue(back.reads.contains("c1")) { "c1 was the LRU entry and must have been evicted" }
	}
}
