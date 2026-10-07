package kadx.api.impl

import kadx.api.ICodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * [BoundedMemoryCodeCache] 的容量与淘汰语义测试。
 *
 * 背景：无界 `InMemoryCodeCache` 在全量代码搜索时会线性增长（实测 8,279 类 +277 MB，
 * 外推 253k 类约 8.5 GB），所以内存模式必须换成有界实现。
 */
class BoundedMemoryCodeCacheTest {

	@Test
	fun evictsLeastRecentlyUsedEntry() {
		val cache = BoundedMemoryCodeCache(capacity = 3)
		for (name in listOf("a", "b", "c")) {
			cache.add(name, SimpleCodeInfo("code-$name"))
		}
		assertThat(cache.size).isEqualTo(3)

		// 访问 a：使 b 变成最久未使用
		assertThat(cache.getCode("a")).isEqualTo("code-a")

		cache.add("d", SimpleCodeInfo("code-d"))

		assertThat(cache.size).isEqualTo(3)
		assertThat(cache.contains("b")).isFalse()
		assertThat(cache.contains("a")).isTrue()
		assertThat(cache.contains("c")).isTrue()
		assertThat(cache.contains("d")).isTrue()
	}

	@Test
	fun neverGrowsBeyondCapacity() {
		val capacity = 16
		val cache = BoundedMemoryCodeCache(capacity)
		for (i in 0 until 1000) {
			cache.add("cls$i", SimpleCodeInfo("code-$i"))
			assertThat(cache.size).isLessThanOrEqualTo(capacity)
		}
		assertThat(cache.size).isEqualTo(capacity)
	}

	@Test
	fun returnsEmptyForUnknownClass() {
		val cache = BoundedMemoryCodeCache(capacity = 2)
		assertThat(cache.get("missing")).isSameAs(ICodeInfo.EMPTY)
		assertThat(cache.getCode("missing")).isNull()
		assertThat(cache.contains("missing")).isFalse()
	}

	@Test
	fun addRefreshesExistingEntryWithoutGrowing() {
		val cache = BoundedMemoryCodeCache(capacity = 2)
		cache.add("a", SimpleCodeInfo("v1"))
		cache.add("b", SimpleCodeInfo("v2"))
		cache.add("a", SimpleCodeInfo("v3"))

		assertThat(cache.size).isEqualTo(2)
		assertThat(cache.getCode("a")).isEqualTo("v3")
	}

	@Test
	fun removeAndCloseDropEntries() {
		val cache = BoundedMemoryCodeCache(capacity = 4)
		cache.add("a", SimpleCodeInfo("code-a"))
		cache.remove("a")
		assertThat(cache.contains("a")).isFalse()

		cache.add("b", SimpleCodeInfo("code-b"))
		cache.close()
		assertThat(cache.size).isZero()
	}

	@Test
	fun rejectsNonPositiveCapacity() {
		assertThatThrownBy { BoundedMemoryCodeCache(capacity = 0) }
			.isInstanceOf(IllegalArgumentException::class.java)
	}
}
