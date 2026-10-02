package jadx.core.plugins.files

import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.data.IJadxFiles
import jadx.core.utils.files.FileUtils
import java.nio.file.Path

/**
 * 插件专属文件目录实现：在基础目录下按插件 id 隔离。
 *
 * **做什么**：把 [IJadxFilesGetter] 提供的配置/缓存/临时目录，映射为
 * `<base>/plugins-data/<pluginId>`，并确保目录已创建。
 *
 * **为什么这样隔离**：不同插件读写各自的目录，避免互相覆盖；
 * 目录名包含插件 id，便于用户排查和清理。
 */
class JadxFilesData(
	private val pluginInfo: JadxPluginInfo,
	private val filesGetter: IJadxFilesGetter,
) : IJadxFiles {

	companion object {
		/** 插件数据在基础目录下的子目录名。 */
		private const val PLUGINS_DATA_DIR = "plugins-data"
	}

	override fun getPluginCacheDir(): Path = toPluginPath(filesGetter.getCacheDir())

	override fun getPluginConfigDir(): Path = toPluginPath(filesGetter.getConfigDir())

	override fun getPluginTempDir(): Path = toPluginPath(filesGetter.getTempDir())

	/** 拼出插件专属目录并创建它，返回最终路径。 */
	private fun toPluginPath(dir: Path): Path {
		val dirPath = dir.resolve(PLUGINS_DATA_DIR).resolve(pluginInfo.getPluginId())
		FileUtils.makeDirs(dirPath)
		return dirPath
	}
}
