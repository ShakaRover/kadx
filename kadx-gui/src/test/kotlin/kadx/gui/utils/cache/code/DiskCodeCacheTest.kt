package kadx.gui.utils.cache.code

import kadx.api.impl.NoOpCodeCache
import kadx.core.dex.nodes.ClassNode
import kadx.gui.cache.code.disk.DiskCodeCache
import kadx.tests.api.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * 磁盘代码缓存测试。
 *
 * **做什么**：把某个类的反编译结果写入 [DiskCodeCache]，再读回并校验代码与元数据一致。
 *
 * 原 Java 版本直接以本测试类为输入，而 [IntegrationTest.getClassNode] 需要同名 `.java`
 * 源文件；迁移到 Kotlin 后改用已编译的 `.class` 文件（[getClassNodeFromFiles]），
 * 避免在 `kadx-gui/src` 下保留 Java 文件。
 */
class DiskCodeCacheTest : IntegrationTest() {

	@field:TempDir
	private lateinit var tempDir: Path

	@Test
	@Throws(IOException::class)
	fun test() {
		disableCompilation()
		getArgs().codeCache = NoOpCodeCache.INSTANCE
		val clsNode = selfClassNode
		val codeInfo = clsNode.getCode()

		val cache = DiskCodeCache(clsNode.root, tempDir)

		val clsKey = clsNode.fullName
		cache.add(clsKey, codeInfo)

		val readCodeInfo = cache.get(clsKey)

		assertThat(readCodeInfo).isNotNull()
		assertThat(readCodeInfo.codeStr).isEqualTo(codeInfo.codeStr)
		assertThat(readCodeInfo.codeMetadata.getLineMapping())
			.isEqualTo(codeInfo.codeMetadata.getLineMapping())
		LOG.info("Disk code annotations: {}", readCodeInfo.codeMetadata.getAsMap())
		assertThat(readCodeInfo.codeMetadata.getAsMap())
			.hasSameSizeAs(codeInfo.codeMetadata.getAsMap())

		cache.close()
	}

	/**
	 * `close()` 必须等所有已提交的写入落盘。
	 *
	 * 旧实现只 `shutdown()` + `awaitTermination(1min)`，超时即返回，队列里剩余类的写入会被
	 * 静默丢弃；这里用「重开同一缓存目录」来观察落盘结果。
	 */
	@Test
	@Throws(IOException::class)
	fun pendingWritesAreFlushedOnClose() {
		disableCompilation()
		getArgs().codeCache = NoOpCodeCache.INSTANCE
		val clsNode = selfClassNode
		val codeInfo = clsNode.getCode()
		val clsKey = clsNode.fullName

		val cache = DiskCodeCache(clsNode.root, tempDir)
		repeat(128) { cache.add(clsKey, codeInfo) }
		cache.close()

		val reopened = DiskCodeCache(clsNode.root, tempDir)
		try {
			assertThat(reopened.contains(clsKey)).isTrue()
			assertThat(reopened.get(clsKey).codeStr).isEqualTo(codeInfo.codeStr)
		} finally {
			reopened.close()
		}
	}

	/**
	 * 线程池已关闭时 `add()` 被拒绝，pending 计数不能泄漏 ——
	 * 否则再次 `close()` 会空等满 10 分钟上限。
	 */
	@Test
	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	@Throws(IOException::class)
	fun addAfterCloseDoesNotLeakPendingCount() {
		disableCompilation()
		getArgs().codeCache = NoOpCodeCache.INSTANCE
		val clsNode = selfClassNode
		val codeInfo = clsNode.getCode()

		val cache = DiskCodeCache(clsNode.root, tempDir)
		cache.close()

		assertThatThrownBy { cache.add(clsNode.fullName, codeInfo) }
			.isInstanceOf(RejectedExecutionException::class.java)

		// 计数未泄漏：再 close() 应立即返回（泄漏则要空等 10 分钟）
		val start = System.currentTimeMillis()
		cache.close()
		assertThat(System.currentTimeMillis() - start).isLessThan(5_000L)
	}

	/** 用当前测试类自身的 `.class` 文件构造 [ClassNode]，绕开对 `.java` 源文件的依赖。 */
	private val selfClassNode: ClassNode get() {
		val clsUrl = checkNotNull(javaClass.getResource("DiskCodeCacheTest.class")) {
			"Test class file not found: ${javaClass.name}"
		}
		return getClassNodeFromFiles(listOf(File(clsUrl.toURI())), javaClass.name)
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(DiskCodeCacheTest::class.java)
	}
}
