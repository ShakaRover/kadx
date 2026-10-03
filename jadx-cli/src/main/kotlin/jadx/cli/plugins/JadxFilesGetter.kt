package jadx.cli.plugins

import jadx.commons.app.JadxCommonFiles
import jadx.commons.app.JadxTempFiles
import jadx.core.plugins.files.IJadxFilesGetter
import java.nio.file.Path

/**
 * 生产环境下 [IJadxFilesGetter] 的实现：把插件需要的目录指向 jadx 的全局目录。
 *
 * **做什么**：配置目录取 `JadxCommonFiles`，临时目录取 `JadxTempFiles`。
 *
 * **为什么这样写**：原 Java 是单例（私有构造器 + `public static final INSTANCE`）。
 * Kotlin 用 `companion object` + `@JvmField` 暴露同名的静态字段 `INSTANCE`，
 * Java 调用方（如 jadx-gui 的 `CollectPlugins`）写法 `JadxFilesGetter.INSTANCE` 保持不变。
 */
class JadxFilesGetter private constructor() : IJadxFilesGetter {

	override fun getConfigDir(): Path = JadxCommonFiles.getConfigDir()

	override fun getCacheDir(): Path = JadxCommonFiles.getCacheDir()

	override fun getTempDir(): Path = JadxTempFiles.tempRootDir

	companion object {
		/** 全局唯一实例（对应原 Java 的 `public static final INSTANCE`）。 */
		@JvmField
		val INSTANCE = JadxFilesGetter()
	}
}
