package kadx.api.plugins.utils

import kadx.zip.IZipEntry
import kadx.zip.ZipReader
import kadx.zip.io.LimitedInputStream
import kadx.zip.security.DisabledZipSecurity
import kadx.zip.security.IKadxZipSecurity
import kadx.zip.security.KadxZipSecurity
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
 * 建议优先使用 [kadx.api.KadxDecompiler.getZipReader] 或
 * `KadxPluginContext.getZipReader()` 已配置好的实例。
 *
 * **做什么**：提供 zip 条目合法性/zip 炸弹检查、路径穿越防护、条目输入流包装等静态方法。
 * **为什么用 `object` + `@JvmStatic`**：原 Java 是私有构造器的静态工具类，保持静态调用方式不变。
 */
@Deprecated("Migrate to ZipReader. Prefer already configured instance from KadxDecompiler.getZipReader() or KadxPluginContext.getZipReader().")
object ZipSecurity {

	private val DISABLE_CHECKS: Boolean = kadx.core.utils.Utils.getEnvVarBool("KADX_DISABLE_ZIP_SECURITY", false)

	private val MAX_ENTRIES_COUNT: Int = kadx.core.utils.Utils.getEnvVarInt("KADX_ZIP_MAX_ENTRIES_COUNT", 100_000)

	private val ZIP_SECURITY: IKadxZipSecurity = buildZipSecurity()

	private val ZIP_READER: ZipReader = ZipReader(ZIP_SECURITY)

	private fun buildZipSecurity(): IKadxZipSecurity {
		if (DISABLE_CHECKS) {
			return DisabledZipSecurity.INSTANCE
		}
		val kadxZipSecurity = KadxZipSecurity()
		kadxZipSecurity.setMaxEntriesCount(MAX_ENTRIES_COUNT)
		return kadxZipSecurity
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
