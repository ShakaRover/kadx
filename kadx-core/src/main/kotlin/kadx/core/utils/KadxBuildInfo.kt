package kadx.core.utils

import kadx.core.Kadx
import kadx.core.utils.exceptions.KadxRuntimeException
import java.io.InputStream
import java.util.Properties

/**
 * kadx 构建信息（版本号、打包类型）。
 *
 * **用途**：启动时读取 classpath 上的 `kadx-build-info.properties`，
 * 找不到时回退到开发版本号。GUI/CLI 展示版本信息时使用。
 *
 * **Kotlin 转换说明**：使用 `object` + `@JvmStatic`，Java 侧
 * `KadxBuildInfo.getKadxVersion()` 调用保持不变。
 */
object KadxBuildInfo {

	/** 构建数据只读快照 */
	private class BuildData(
		val kadxVersion: String,
		val kadxBundleType: String,
	) {

		override fun toString(): String = "{kadx-version:$kadxVersion, kadx-bundle-type:$kadxBundleType}"
	}

	/** 类加载时解析一次，避免重复读属性文件 */
	private val BUILD_DATA: BuildData = load()

	private fun load(): BuildData {
		try {
			val input: InputStream? = KadxBuildInfo::class.java.getResourceAsStream("/kadx-build-info.properties")
			input.use { stream ->
				if (stream == null) {
					throw IllegalStateException("kadx-build-info.properties not found")
				}
				val props = Properties()
				props.load(stream)
				val version = props.getProperty("kadx-version", Kadx.VERSION_DEV)
				val bundleType = props.getProperty("kadx-bundle-type", "")
				return BuildData(version, bundleType)
			}
		} catch (e: Exception) {
			throw KadxRuntimeException("Failed to load build properties", e)
		}
	}

	fun getKadxBundleType(): String = BUILD_DATA.kadxBundleType

	fun getKadxVersion(): String = BUILD_DATA.kadxVersion
}
