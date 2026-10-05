package kadx.tests.api.compiler

import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory

/**
 * Java 版本探测工具。
 *
 * **Java 兼容性**：`JAVA_VERSION_INT` 保持为 `object` 的 `@JvmField`（JVM 静态字段），
 * `checkJavaVersion` 标注 `@JvmStatic`，因此 Java 侧仍可写 `JavaUtils.JAVA_VERSION_INT`
 * 与 `JavaUtils.checkJavaVersion(11)`。
 */
object JavaUtils {

	private val LOG = LoggerFactory.getLogger(JavaUtils::class.java)

	/** 当前 JVM 的 Java 规范版本（如 8、11、17）；无法识别时回退为 8。 */
	@JvmField
	val JAVA_VERSION_INT: Int = getJavaVersionInt()

	/** 当前 Java 版本是否不低于 [requiredVersion]。 */
	@JvmStatic
	fun checkJavaVersion(requiredVersion: Int): Boolean = JAVA_VERSION_INT >= requiredVersion

	private fun getJavaVersionInt(): Int {
		val javaSpecVerStr = SystemUtils.JAVA_SPECIFICATION_VERSION
		if (javaSpecVerStr == null) {
			LOG.warn("Unknown current java specification version, use 8 as fallback")
			return 8 // fallback version
		}
		if (javaSpecVerStr.startsWith("1.")) {
			return javaSpecVerStr.substring(2).toInt()
		}
		return javaSpecVerStr.toInt()
	}
}
