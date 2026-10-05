package kadx.tests.api.utils

import kadx.core.plugins.files.IKadxFilesGetter
import kadx.core.utils.files.FileUtils
import java.nio.file.Path

/**
 * 测试用的 [IKadxFilesGetter] 实现：所有目录都指向 JUnit 注入的临时目录。
 *
 * 避免测试向用户真实的配置/缓存目录写入数据。
 */
class TestFilesGetter(private val testDir: Path) : IKadxFilesGetter {

	override fun getConfigDir(): Path = makeSubDir("config")

	override fun getCacheDir(): Path = makeSubDir("cache")

	override fun getTempDir(): Path = testDir

	private fun makeSubDir(config: String): Path {
		val dir = testDir.resolve(config)
		FileUtils.makeDirs(dir)
		return dir
	}
}
