package jadx.api.security.impl

import jadx.api.security.IJadxSecurity
import jadx.api.security.JadxSecurityFlag
import jadx.api.security.SanitizeType
import jadx.core.deobf.NameMapper
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.zip.IZipEntry
import jadx.zip.security.DisabledZipSecurity
import jadx.zip.security.IJadxZipSecurity
import jadx.zip.security.JadxZipSecurity
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import java.io.File
import java.io.InputStream
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [IJadxSecurity] 的默认实现：按 [JadxSecurityFlag] 开关组合各项安全防护。
 *
 * **做什么**：
 * - zip 相关校验直接委托给内部的 [zipSecurity]；
 * - 包名校验（[verifyAppPackage]）；
 * - 字符串转义（[sanitizeString]，当前 Groovy / Kotlin 用同一套规则）；
 * - XML 解析（[parseXml]，安全模式下禁用 DTD 与外部实体，防 XXE）。
 *
 * **为什么用两个显式构造器**：原 Java 有“仅开关”和“开关 + 自定义 zip 安全策略”
 * 两个构造器，保留成 Kotlin 次级构造器后 JVM 签名与原来完全一致，Java 调用方零改动。
 */
class JadxSecurity : IJadxSecurity {

	private val flags: Set<JadxSecurityFlag>
	private val zipSecurity: IJadxZipSecurity

	/** 仅按开关构造：根据 [JadxSecurityFlag.SECURE_ZIP_READER] 选择默认的 zip 安全实现。 */
	constructor(flags: Set<JadxSecurityFlag>) {
		this.flags = flags
		this.zipSecurity = if (flags.contains(JadxSecurityFlag.SECURE_ZIP_READER)) JadxZipSecurity() else DisabledZipSecurity.INSTANCE
	}

	/** 使用外部传入的 zip 安全实现构造。 */
	constructor(flags: Set<JadxSecurityFlag>, zipSecurity: IJadxZipSecurity) {
		this.flags = flags
		this.zipSecurity = zipSecurity
	}

	override fun isValidEntry(entry: IZipEntry): Boolean = zipSecurity.isValidEntry(entry)

	override fun isValidEntryName(entryName: String): Boolean = zipSecurity.isValidEntryName(entryName)

	override fun isInSubDirectory(baseDir: File, file: File): Boolean = zipSecurity.isInSubDirectory(baseDir, file)

	override fun useLimitedDataStream(): Boolean = zipSecurity.useLimitedDataStream()

	override fun getMaxEntriesCount(): Int = zipSecurity.getMaxEntriesCount()

	override fun verifyAppPackage(appPackage: String?): String? {
		if (flags.contains(JadxSecurityFlag.VERIFY_APP_PACKAGE) && !NameMapper.isValidFullIdentifier(appPackage)) {
			LOG.warn("App package '{}' has invalid format and will be ignored", appPackage)
			return "INVALID_PACKAGE"
		}
		return appPackage
	}

	override fun sanitizeString(str: String, type: SanitizeType): String {
		if (!flags.contains(JadxSecurityFlag.SANITIZE_STRINGS)) {
			return str
		}
		when (type) {
			SanitizeType.GRADLE_GROOVY, SanitizeType.GRADLE_KOTLIN ->
				// 目前 Groovy 与 Kotlin DSL 使用同一套需要清理的字符集合
				return sanitizeGradle(str)
		}
		throw JadxRuntimeException("Unsupported sanitize type: $type")
	}

	/** 去掉会破坏 Gradle 脚本的字符（引号、分号、`$`、`{}`、路径分隔符、通配符、方括号、反斜杠）。 */
	private fun sanitizeGradle(str: String): String {
		val matcher = SANITIZE_GRADLE_PATTERN.matcher(str)
		if (matcher.find()) {
			return matcher.replaceAll("")
		}
		return str
	}

	override fun parseXml(inputStream: InputStream): Document {
		val dbf: DocumentBuilderFactory
		if (flags.contains(JadxSecurityFlag.SECURE_XML_PARSER)) {
			dbf = SecureDBFHolder.INSTANCE
		} else {
			dbf = SimpleDBFHolder.INSTANCE
		}
		try {
			return dbf.newDocumentBuilder().parse(inputStream)
		} catch (e: Exception) {
			throw RuntimeException("Failed to parse xml", e)
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxSecurity::class.java)

		/** 需要从 Gradle 脚本字符串中清理掉的字符集合。 */
		private val SANITIZE_GRADLE_PATTERN: Pattern = Pattern.compile("[;'\"${'$'}{}/:>?*|\\[\\]\\\\]")
	}

	/** 普通 XML 解析器的持有者（懒加载单例，等价原 Java 的静态内部类 + static final 字段）。 */
	private object SimpleDBFHolder {
		val INSTANCE: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
	}

	/** 安全 XML 解析器的持有者：首次访问时构建并加固解析器（禁用 DTD / 外部实体，防 XXE）。 */
	private object SecureDBFHolder {
		val INSTANCE: DocumentBuilderFactory = buildSecureDBF()

		private fun buildSecureDBF(): DocumentBuilderFactory = try {
			val dbf = DocumentBuilderFactory.newInstance()
			dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
			dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
			dbf.setFeature("http://xml.org/sax/features/external-general-entities", false)
			dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
			dbf.setFeature("http://apache.org/xml/features/dom/create-entity-ref-nodes", false)
			dbf.isXIncludeAware = false
			dbf.isExpandEntityReferences = false
			dbf
		} catch (e: Exception) {
			throw RuntimeException("Fail to build secure XML DocumentBuilderFactory", e)
		}
	}
}
