package jadx.core.plugins.files

import jadx.core.utils.files.FileUtils
import java.nio.file.Path

/**
 * 把所有 jadx 文件都放在同一个基础目录下的文件目录提供者。
 *
 * **用途**：CLI / 测试场景下把配置、缓存、临时目录统一放在指定的工作目录中，
 * 便于隔离与清理。
 *
 * @param baseDir 所有子目录的根目录
 */
class SingleDirFilesGetter(private val baseDir: Path) : IJadxFilesGetter {

	override fun getConfigDir(): Path = makeSubDir("config")

	override fun getCacheDir(): Path = makeSubDir("cache")

	override fun getTempDir(): Path = makeSubDir("temp")

	/** 在基础目录下解析并创建子目录。 */
	private fun makeSubDir(subDir: String): Path {
		val dir = baseDir.resolve(subDir)
		FileUtils.makeDirs(dir)
		return dir
	}
}
