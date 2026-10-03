package jadx.gui.utils.files

import jadx.commons.app.JadxCommonFiles
import java.nio.file.Path

/**
 * GUI 使用的关键文件与目录路径。
 *
 * **做什么**：集中定义配置目录下的 `gui.json`、缓存列表文件，以及项目缓存目录。
 *
 * **为什么用 `@JvmField`**：这些常量被 Java 代码以静态字段方式访问
 * （如 `CacheManager` 的 `JadxFiles.CACHES_LIST`），必须保持字段级 JVM 表面不变。
 */
class JadxFiles {

	companion object {
		private val CONFIG_DIR: Path = JadxCommonFiles.getConfigDir()

		/** GUI 配置文件。 */
		@JvmField
		val GUI_CONF: Path = CONFIG_DIR.resolve("gui.json")

		/** 缓存列表文件。 */
		@JvmField
		val CACHES_LIST: Path = CONFIG_DIR.resolve("caches.json")

		/** 缓存根目录。 */
		@JvmField
		val CACHE_DIR: Path = JadxCommonFiles.getCacheDir()

		/** 项目缓存目录。 */
		@JvmField
		val PROJECTS_CACHE_DIR: Path = CACHE_DIR.resolve("projects")
	}
}
