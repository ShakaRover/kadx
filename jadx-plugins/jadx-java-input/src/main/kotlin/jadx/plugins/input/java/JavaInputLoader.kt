package jadx.plugins.input.java

import jadx.api.plugins.utils.CommonFileUtils
import jadx.core.plugins.files.TempFilesGetter
import jadx.core.utils.files.FileUtils
import jadx.zip.IZipEntry
import jadx.zip.ZipReader
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Java 输入加载器：把 .class/.jar/.zip 文件收集成 [JavaClassReader] 列表。
 *
 **做什么**：按文件头魔数（CAFEBABE / PK\x03\x04）或扩展名识别 class 与 zip，
 * zip 递归展开逐条目处理；每个 reader 分配自增唯一 id。
 */
class JavaInputLoader(
	private val zipReader: ZipReader,
	private val tempPath: Path,
) {

	@Deprecated("Use JavaInputLoader(zipReader, tempPath)")
	constructor() : this(ZipReader(), TempFilesGetter.INSTANCE.getTempDir())

	private var classUniqId = 1

	fun collectFiles(inputFiles: List<Path>): List<JavaClassReader> = inputFiles.stream()
		.map { it.toFile() }
		.map { loadFromFile(it) }
		.filter { !it.isEmpty() }
		.flatMap { it.stream() }
		.collect(java.util.stream.Collectors.toList())

	fun loadInputStream(input: InputStream, name: String): List<JavaClassReader> = loadReader(input, name, null, null)

	fun loadClass(content: ByteArray, fileName: String): JavaClassReader = JavaClassReader(getNextUniqId(), fileName, content)

	private fun loadFromFile(file: File): List<JavaClassReader> = try {
		BufferedInputStream(FileInputStream(file)).use { inputStream ->
			loadReader(inputStream, file.name, file, null)
		}
	} catch (e: Exception) {
		LOG.error("File open error: {}", file.absolutePath, e)
		emptyList()
	}

	// 原 Java 参数名 in 是 Kotlin 保留字，重命名（位置传参，不影响 API）
	private fun loadReader(
		input: InputStream,
		name: String,
		@Nullable file: File?,
		@Nullable parentFileName: String?,
	): List<JavaClassReader> {
		val magic = ByteArray(MAX_MAGIC_SIZE)
		if (input.read(magic) != magic.size) {
			return emptyList()
		}
		if (isStartWithBytes(magic, JAVA_CLASS_FILE_MAGIC) || name.endsWith(".class")) {
			val data = CommonFileUtils.loadBytes(magic, input)
			val source = concatSource(parentFileName, name)
			val reader = JavaClassReader(getNextUniqId(), source, data)
			return listOf(reader)
		}
		if (isStartWithBytes(magic, ZIP_FILE_MAGIC) || CommonFileUtils.isZipFileExt(name)) {
			if (file != null) {
				return collectFromZip(file, name)
			}
			val zipFile = CommonFileUtils.saveToTempFile(magic, input, ".zip").toFile()
			val readers = collectFromZip(zipFile, concatSource(parentFileName, name))
			CommonFileUtils.safeDeleteFile(zipFile)
			return readers
		}
		return emptyList()
	}

	private fun loadReaderFromZipEntry(content: ByteArray, name: String, parentFileName: String): List<JavaClassReader> {
		if (isStartWithBytes(content, JAVA_CLASS_FILE_MAGIC) || name.endsWith(".class")) {
			val source = concatSource(parentFileName, name)
			val reader = JavaClassReader(getNextUniqId(), source, content)
			return listOf(reader)
		}
		if (isStartWithBytes(content, ZIP_FILE_MAGIC) || CommonFileUtils.isZipFileExt(name)) {
			val tempZip = Files.createTempFile(tempPath, "temp", ".zip")
			FileUtils.writeFile(tempZip, content)
			val zipFile = tempZip.toFile()
			val readers = collectFromZip(zipFile, concatSource(parentFileName, name))
			CommonFileUtils.safeDeleteFile(zipFile)
			return readers
		}
		return emptyList()
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JavaInputLoader::class.java)

		private const val MAX_MAGIC_SIZE = 4
		private val JAVA_CLASS_FILE_MAGIC = byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte())
		private val ZIP_FILE_MAGIC = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())

		/** @JvmStatic 供 Java 调用方（如测试）复用魔数判断 */
		@JvmStatic
		fun isStartWithBytes(fileMagic: ByteArray, expectedBytes: ByteArray): Boolean {
			val len = expectedBytes.size
			if (fileMagic.size < len) {
				return false
			}
			for (i in 0 until len) {
				if (fileMagic[i] != expectedBytes[i]) {
					return false
				}
			}
			return true
		}
	}

	private fun concatSource(@Nullable parentFileName: String?, name: String): String {
		if (parentFileName == null) {
			return name
		}
		return parentFileName + ':' + name
	}

	private fun collectFromZip(file: File, name: String): List<JavaClassReader> {
		val result = ArrayList<JavaClassReader>()
		try {
			zipReader.open(file).use { zip ->
				for (entry in zip.getEntries()) {
					if (entry.isDirectory()) {
						continue
					}
					val entryName = entry.getName()
					if (entryName.startsWith("META-INF/versions/")) {
						// skip classes for different java versions
						continue
					}
					try {
						val readers: List<JavaClassReader>
						if (entry.preferBytes()) {
							readers = loadReaderFromZipEntry(entry.getBytes(), entryName, name)
						} else {
							readers = loadReader(entry.getInputStream(), entryName, null, name)
						}
						result.addAll(readers)
					} catch (e: Exception) {
						LOG.error("Failed to read zip entry: {}", entry, e)
					}
				}
			}
		} catch (e: Exception) {
			LOG.error("Failed to process zip file: {}", name, e)
		}
		return result
	}

	private fun getNextUniqId(): Int = classUniqId++
}
