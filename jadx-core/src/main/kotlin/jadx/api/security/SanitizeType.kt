package jadx.api.security

/**
 * 支持的字符串清理（sanitize / escape）类型。
 *
 * **做什么**：导出 Gradle 工程文件时，需要把用户可控的字符串（包名、类名等）
 * 转义成对应构建脚本语言的安全字面量；本枚举用于区分目标语言。
 *
 * **为什么这样写**：这是公共 API，枚举常量名必须与原 Java 完全一致，供
 * [IJadxSecurity.sanitizeString] 的实现方与调用方使用。
 */
enum class SanitizeType {
	/** Gradle Groovy DSL 脚本。 */
	GRADLE_GROOVY,

	/** Gradle Kotlin DSL 脚本。 */
	GRADLE_KOTLIN,
}
