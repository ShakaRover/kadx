package jadx.gui.utils.cache.code

import jadx.api.impl.NoOpCodeCache
import jadx.core.dex.nodes.ClassNode
import jadx.gui.cache.code.disk.DiskCodeCache
import jadx.tests.api.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Path

/**
 * 磁盘代码缓存测试。
 *
 * **做什么**：把某个类的反编译结果写入 [DiskCodeCache]，再读回并校验代码与元数据一致。
 *
 * 原 Java 版本直接以本测试类为输入，而 [IntegrationTest.getClassNode] 需要同名 `.java`
 * 源文件；迁移到 Kotlin 后改用已编译的 `.class` 文件（[getClassNodeFromFiles]），
 * 避免在 `jadx-gui/src` 下保留 Java 文件。
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
