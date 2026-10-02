@file:Suppress("ktlint:standard:property-naming")

package jadx.api.plugins.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * 插件通用文件工具。
 *
 * **做什么**：提供当前工作目录常量、临时文件保存、流读写、文件扩展名处理等静态辅助方法。
 *
 * **为什么用 `object` + `@JvmStatic`**：原 Java 是纯静态工具类（私有构造器），
 * Kotlin 用 `object` 表达单例；`@JvmStatic` 让 Java 侧仍以 `CommonFileUtils.xxx()` 调用。
 * [CWD] / [CWD_PATH] 是原 `public static final` 字段，用 `@JvmField` 保持静态字段访问。
 */
object CommonFileUtils {

	private val LOG: Logger = LoggerFactory.getLogger(CommonFileUtils::class.java)

	/** 当前工作目录（已解析为规范路径）。 */
	@JvmField
	val CWD: File = getCWD()

	/** [CWD] 对应的 [Path]。 */
	@JvmField
	val CWD_PATH: Path = CWD.toPath()

	private fun getCWD(): File = try {
		File(".").canonicalFile
	} catch (e: IOException) {
		throw RuntimeException("Failed to init current working dir constant", e)
	}

	/** 把输入流保存到临时文件（无前缀数据）。 */
	@JvmStatic
	@Throws(IOException::class)
	fun saveToTempFile(input: InputStream, suffix: String): Path = saveToTempFile(null, input, suffix)

	/** 把输入流保存到临时文件，可先写入一段数据前缀。 */
	@JvmStatic
	@Throws(IOException::class)
	fun saveToTempFile(dataPrefix: ByteArray?, input: InputStream, suffix: String): Path {
		val tempFile = Files.createTempFile("jadx-temp-", suffix)
		try {
			Files.newOutputStream(tempFile).use { out ->
				if (dataPrefix != null) {
					out.write(dataPrefix)
				}
				copyStream(input, out)
			}
		} catch (e: Exception) {
			throw IOException("Failed to save temp file", e)
		}
		return tempFile
	}

	/** 安全删除文件：删除失败只记录日志，不抛异常。 */
	@JvmStatic
	fun safeDeleteFile(file: File): Boolean = try {
		file.delete()
	} catch (e: Exception) {
		LOG.warn("Failed to delete file: {}", file, e)
		false
	}

	/** 读取整个输入流为字节数组（无前缀数据）。 */
	@JvmStatic
	@Throws(IOException::class)
	fun loadBytes(input: InputStream): ByteArray = loadBytes(null, input)

	/** 读取整个输入流为字节数组，可拼接一段数据前缀。 */
	@JvmStatic
	@Throws(IOException::class)
	fun loadBytes(dataPrefix: ByteArray?, input: InputStream): ByteArray {
		val estimateSize = if (dataPrefix == null) input.available() else dataPrefix.size + input.available()
		try {
			ByteArrayOutputStream(estimateSize).use { out ->
				if (dataPrefix != null) {
					out.write(dataPrefix)
				}
				copyStream(input, out)
				return out.toByteArray()
			}
		} catch (e: Exception) {
			throw IOException("Failed to read input stream to bytes array", e)
		}
	}

	/** 把 [input] 的全部内容拷贝到 [output]（8KB 缓冲）。 */
	@JvmStatic
	@Throws(IOException::class)
	fun copyStream(input: InputStream, output: OutputStream) {
		val buffer = ByteArray(8192)
		while (true) {
			val count = input.read(buffer)
			if (count == -1) {
				break
			}
			output.write(buffer, 0, count)
		}
	}

	/** 取文件扩展名；没有扩展名时返回 null。 */
	@JvmStatic
	fun getFileExtension(fileName: String): String? {
		val dotIndex = fileName.lastIndexOf('.')
		if (dotIndex == -1) {
			return null
		}
		return fileName.substring(dotIndex + 1)
	}

	/** 去掉文件扩展名；没有扩展名时原样返回。 */
	@JvmStatic
	fun removeFileExtension(fileName: String): String {
		val dotIndex = fileName.lastIndexOf('.')
		if (dotIndex == -1) {
			return fileName
		}
		return fileName.substring(0, dotIndex)
	}

	private val ZIP_FILE_EXTS: Set<String> = Utils.constSet("zip", "jar", "apk")

	/** 判断文件名是否为 zip 类扩展名（zip/jar/apk）。 */
	@JvmStatic
	fun isZipFileExt(fileName: String): Boolean {
		val ext = getFileExtension(fileName) ?: return false
		return ZIP_FILE_EXTS.contains(ext)
	}
}
