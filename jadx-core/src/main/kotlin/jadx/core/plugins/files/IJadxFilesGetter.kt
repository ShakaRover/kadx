package jadx.core.plugins.files

import java.nio.file.Path

/**
 * 插件文件目录提供者接口。
 *
 * **做什么**：向 jadx 提供三个基础目录（配置、缓存、临时），插件专属目录由
 * [JadxFilesData] 在其下再拼接 `plugins-data/<pluginId>` 得到。
 *
 * **为什么保留 Java 风格的显式 getter**：本接口有多个 Java 实现
 * （`TestFilesGetter`、`JadxFilesGetter` 等）与一个 Kotlin 实现
 * （[SingleDirFilesGetter]、[TempFilesGetter]），保持 `getConfigDir()` 等
 * JVM 方法名不变，Java 实现类零改动。
 */
interface IJadxFilesGetter {

	/** 配置目录（存放用户可编辑的插件配置）。 */
	fun getConfigDir(): Path

	/** 缓存目录（可安全删除、可重新生成的数据）。 */
	fun getCacheDir(): Path

	/** 临时目录（进程退出后可清理的中间文件）。 */
	fun getTempDir(): Path
}
