package jadx.core.utils

import jadx.core.Jadx
import jadx.core.utils.exceptions.JadxRuntimeException
import java.io.InputStream
import java.util.Properties

/**
 * jadx 构建信息（版本号、打包类型）。
 *
 * **用途**：启动时读取 classpath 上的 `jadx-build-info.properties`，
 * 找不到时回退到开发版本号。GUI/CLI 展示版本信息时使用。
 *
 * **Kotlin 转换说明**：使用 `object` + `@JvmStatic`，Java 侧
 * `JadxBuildInfo.getJadxVersion()` 调用保持不变。
 */
object JadxBuildInfo {

	/** 构建数据只读快照 */
	private class BuildData(
		private val jadxVersion: String,
		private val jadxBundleType: String,
	) {
		fun getJadxVersion(): String = jadxVersion

		fun getJadxBundleType(): String = jadxBundleType

		override fun toString(): String = "{jadx-version:$jadxVersion, jadx-bundle-type:$jadxBundleType}"
	}

	/** 类加载时解析一次，避免重复读属性文件 */
	private val BUILD_DATA: BuildData = load()

	private fun load(): BuildData {
		try {
			val input: InputStream? = JadxBuildInfo::class.java.getResourceAsStream("/jadx-build-info.properties")
			input.use { stream ->
				if (stream == null) {
					throw IllegalStateException("jadx-build-info.properties not found")
				}
				val props = Properties()
				props.load(stream)
				val version = props.getProperty("jadx-version", Jadx.VERSION_DEV)
				val bundleType = props.getProperty("jadx-bundle-type", "")
				return BuildData(version, bundleType)
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to load build properties", e)
		}
	}

	@JvmStatic
	fun getJadxBundleType(): String = BUILD_DATA.getJadxBundleType()

	@JvmStatic
	fun getJadxVersion(): String = BUILD_DATA.getJadxVersion()
}
