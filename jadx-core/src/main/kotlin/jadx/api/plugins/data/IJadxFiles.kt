package jadx.api.plugins.data

import java.nio.file.Path

/**
 * 插件专属的文件/目录访问接口。
 *
 * **做什么**：为某个插件提供隔离的缓存、配置与临时目录，避免插件之间互相干扰。
 *
 * **为什么保持 Java 可实现**：实现类 `JadxFilesData` 是 jadx-core 的 Java 类，
 * 三个 getter 的 JVM 签名保持与原 Java 一致。
 */
interface IJadxFiles {

	/**
	 * 插件缓存目录。
	 */
	fun getPluginCacheDir(): Path

	/**
	 * 插件配置目录。
	 */
	fun getPluginConfigDir(): Path

	/**
	 * 插件临时目录。
	 */
	fun getPluginTempDir(): Path
}
