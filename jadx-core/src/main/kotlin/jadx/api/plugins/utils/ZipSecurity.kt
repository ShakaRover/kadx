package jadx.api.plugins.utils

import jadx.zip.IZipEntry
import jadx.zip.ZipReader
import jadx.zip.io.LimitedInputStream
import jadx.zip.security.DisabledZipSecurity
import jadx.zip.security.IJadxZipSecurity
import jadx.zip.security.JadxZipSecurity
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 已废弃的 zip 安全检查工具，请迁移到 [ZipReader]。
 *
 * 建议优先使用 [jadx.api.JadxDecompiler.getZipReader] 或
 * `JadxPluginContext.getZipReader()` 已配置好的实例。
 *
 * **做什么**：提供 zip 条目合法性/zip 炸弹检查、路径穿越防护、条目输入流包装等静态方法。
 * **为什么用 `object` + `@JvmStatic`**：原 Java 是私有构造器的静态工具类，保持静态调用方式不变。
 */
@Deprecated("Migrate to ZipReader. Prefer already configured instance from JadxDecompiler.getZipReader() or JadxPluginContext.getZipReader().")
object ZipSecurity {

	private val DISABLE_CHECKS: Boolean = jadx.core.utils.Utils.getEnvVarBool("JADX_DISABLE_ZIP_SECURITY", false)

	private val MAX_ENTRIES_COUNT: Int = jadx.core.utils.Utils.getEnvVarInt("JADX_ZIP_MAX_ENTRIES_COUNT", 100_000)

	private val ZIP_SECURITY: IJadxZipSecurity = buildZipSecurity()

	private val ZIP_READER: ZipReader = ZipReader(ZIP_SECURITY)

	private fun buildZipSecurity(): IJadxZipSecurity {
		if (DISABLE_CHECKS) {
			return DisabledZipSecurity.INSTANCE
		}
		val jadxZipSecurity = JadxZipSecurity()
		jadxZipSecurity.setMaxEntriesCount(MAX_ENTRIES_COUNT)
		return jadxZipSecurity
	}

	@JvmStatic
	fun isInSubDirectory(baseDir: File, file: File): Boolean = ZIP_SECURITY.isInSubDirectory(baseDir, file)

	/**
	 * 检查条目名不包含任何路径穿越（如 `../classes.dex`），
	 * 从而把输出限制在指定目录内。
	 */
	@JvmStatic
	fun isValidZipEntryName(entryName: String): Boolean = ZIP_SECURITY.isValidEntryName(entryName)

	@JvmStatic
	fun isZipBomb(entry: IZipEntry): Boolean = !ZIP_SECURITY.isValidEntry(entry)

	@JvmStatic
	fun isValidZipEntry(entry: IZipEntry): Boolean = ZIP_SECURITY.isValidEntry(entry)

	@JvmStatic
	@Throws(IOException::class)
	fun getInputStreamForEntry(zipFile: ZipFile, entry: ZipEntry): InputStream {
		if (DISABLE_CHECKS) {
			return BufferedInputStream(zipFile.getInputStream(entry))
		}
		val inputStream = zipFile.getInputStream(entry)
		val limited = LimitedInputStream(inputStream, entry.getSize())
		return BufferedInputStream(limited)
	}

	/**
	 * 遍历 zip 中的合法条目。
	 * 访问器返回非 null 值时停止遍历，并返回该值。
	 */
	@JvmStatic
	fun <R> visitZipEntries(file: File, visitor: Function<IZipEntry, R?>): R? = ZIP_READER.visitEntries(file, visitor)

	@JvmStatic
	fun readZipEntries(file: File, visitor: BiConsumer<IZipEntry, InputStream>) {
		ZIP_READER.readEntries(file, visitor)
	}
}
