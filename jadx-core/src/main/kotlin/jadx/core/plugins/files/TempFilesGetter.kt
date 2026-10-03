package jadx.core.plugins.files

import jadx.core.utils.files.FileUtils
import java.nio.file.Files
import java.nio.file.Path

/**
 * 使用系统临时目录的默认文件目录提供者。
 *
 * **做什么**：在系统临时目录下创建一个 `jadx-temp-*` 根目录，并在其下提供
 * `config` / `cache` / `tmp` 三个子目录；进程退出时根目录会自动删除。
 *
 * **为什么用嵌套 `object TempRootHolder`**：原 Java 用静态内部类持有静态字段，
 * 保证临时根目录只在第一次访问时创建（懒加载）且全局唯一；Kotlin 用
 * `object` 保持同样的「只初始化一次」语义。
 */
class TempFilesGetter private constructor() : IJadxFilesGetter {

	/** 懒加载的临时根目录持有者。 */
	private object TempRootHolder {
		/** 进程级临时根目录；创建失败时直接抛异常终止初始化。 */
		val TEMP_ROOT_DIR: Path = try {
			val dir = Files.createTempDirectory("jadx-temp-")
			dir.toFile().deleteOnExit()
			dir
		} catch (e: Exception) {
			throw RuntimeException("Failed to create temp directory", e)
		}
	}

	companion object {
		/** 全局单例。 */
		val INSTANCE: TempFilesGetter = TempFilesGetter()
	}

	override fun getConfigDir(): Path = makeSubDir("config")

	override fun getCacheDir(): Path = makeSubDir("cache")

	override fun getTempDir(): Path = makeSubDir("tmp")

	/** 在临时根目录下解析并创建子目录。 */
	private fun makeSubDir(subDir: String): Path {
		val dir = TempRootHolder.TEMP_ROOT_DIR.resolve(subDir)
		FileUtils.makeDirs(dir)
		return dir
	}
}
