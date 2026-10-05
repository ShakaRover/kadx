package kadx.cli.plugins

import kadx.commons.app.KadxCommonFiles
import kadx.commons.app.KadxTempFiles
import kadx.core.plugins.files.IKadxFilesGetter
import java.nio.file.Path

/**
 * 生产环境下 [IKadxFilesGetter] 的实现：把插件需要的目录指向 kadx 的全局目录。
 *
 * **做什么**：配置目录取 `KadxCommonFiles`，临时目录取 `KadxTempFiles`。
 *
 * **为什么这样写**：原 Java 是单例（私有构造器 + `public static final INSTANCE`）。
 * Kotlin 用 `companion object` 暴露同名单例属性 `INSTANCE`，
 * 调用方（如 kadx-gui 的 `CollectPlugins`）写法 `KadxFilesGetter.INSTANCE` 保持不变。
 */
class KadxFilesGetter private constructor() : IKadxFilesGetter {

	override fun getConfigDir(): Path = KadxCommonFiles.getConfigDir()

	override fun getCacheDir(): Path = KadxCommonFiles.getCacheDir()

	override fun getTempDir(): Path = KadxTempFiles.tempRootDir

	companion object {
		/** 全局唯一实例（对应原 Java 的 `public static final INSTANCE`）。 */
		val INSTANCE = KadxFilesGetter()
	}
}
