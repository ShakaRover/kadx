package kadx.commons.app

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 临时目录管理。
 *
 * 启动时（类加载时）创建一次 kadx 的临时根目录：
 * 如果设置了环境变量 KADX_TMP_DIR，则在该目录下为每次运行创建独立的实例子目录；
 * 否则使用系统默认 temp 目录。
 */
class KadxTempFiles {
	companion object {
		/** 实例临时目录名前缀，形如 kadx-instance-1234567890123  */
		private const val KADX_TMP_INSTANCE_PREFIX = "kadx-instance-"

		// 与原来 Java 版一致：static final 字段在类加载时立即创建（companion 对象随外层类一起初始化）
		// Kotlin 会自动生成 getter getTempRootDir()，@JvmStatic 让 Java 代码仍可按 KadxTempFiles.getTempRootDir() 静态调用
		@JvmStatic
		val tempRootDir: Path = createTempRootDir()

		private fun createTempRootDir(): Path {
			try {
				val kadxTmpDir = System.getenv("KADX_TMP_DIR")
				// 优先使用用户自定义的临时目录（KADX_TMP_DIR），否则回退到系统默认位置
				val dir: Path = if (kadxTmpDir != null) {
					val customTmpRootDir = Paths.get(kadxTmpDir)
					Files.createDirectories(customTmpRootDir) // 目录不存在时先创建父目录
					// 在自定义根目录下再建一个带随机后缀的实例子目录，避免多次运行互相覆盖
					Files.createTempDirectory(customTmpRootDir, KADX_TMP_INSTANCE_PREFIX)
				} else {
					Files.createTempDirectory(KADX_TMP_INSTANCE_PREFIX)
				}
				dir.toFile().deleteOnExit() // JVM 退出时自动删除实例目录本身（不递归删内容）
				return dir
			} catch (e: Exception) {
				throw RuntimeException("Failed to create temp root directory", e)
			}
		}
	}
}
