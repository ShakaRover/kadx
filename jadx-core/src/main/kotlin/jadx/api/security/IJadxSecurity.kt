package jadx.api.security

import jadx.zip.security.IJadxZipSecurity
import org.w3c.dom.Document
import java.io.InputStream

/**
 * jadx 安全策略接口：在 [IJadxZipSecurity]（zip 安全）之上，增加包名校验、
 * 安全 XML 解析与字符串转义能力。
 *
 * **做什么**：把“处理不可信输入时的所有防护点”集中到一个接口，方便用户按需
 * 关闭或替换（见 `JadxSecurityFlag`）。
 *
 * **为什么保持接口方法形态**：本接口会被外部实现/调用，方法名必须与原 Java 一致。
 * 注意参数名不能叫 `in`（Kotlin 关键字），这里改名为 `inputStream`，不影响 JVM 签名。
 */
interface IJadxSecurity : IJadxZipSecurity {

	/**
	 * 校验应用包名是否安全。
	 *
	 * 原 Java 未标注 `@NotNull`，允许传入 `null` 并原样返回，因此这里如实声明为可空。
	 *
	 * @return 规范化/清理后的字符串；安全时返回原字符串（可能为 `null`）
	 */
	fun verifyAppPackage(appPackage: String?): String?

	/**
	 * XML 文档解析器。
	 *
	 * 根据安全开关决定是否启用禁止 DTD / 外部实体的安全实现。
	 */
	fun parseXml(inputStream: InputStream): Document

	/**
	 * 按 [type] 描述的使用场景，对字符串做清理/转义，使其可安全使用。
	 */
	fun sanitizeString(str: String, type: SanitizeType): String
}
