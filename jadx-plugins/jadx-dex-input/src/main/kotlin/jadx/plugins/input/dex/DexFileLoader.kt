package jadx.plugins.input.dex

import jadx.api.plugins.utils.CommonFileUtils
import jadx.core.utils.files.FileUtils
import jadx.plugins.input.dex.sections.DexConsts
import jadx.plugins.input.dex.sections.DexHeaderV41
import jadx.plugins.input.dex.utils.DexCheckSum
import jadx.zip.ZipReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path

/**
 * DEX 文件加载器：从 .dex / .apk / .zip 输入中读取并解析出 [DexReader] 列表。
 *
 * **背景**：
 * 1. [collectDexFiles] 对每个输入路径先尝试作为 DEX 文件加载，失败时自动回退到 zip 解包；
 * 2. DEX v41（header 扩展）开始，单个文件内可能包含多个子 DEX 结构，
 *    因此 [loadDexReaders] 先尝试 [DexHeaderV41.readIfPresent]，命中则按子 DEX 偏移拆分加载；
 * 3. [getNextUniqId] 生成进程内唯一 id 并供其他插件共享（因此放在 companion object 中）；
 * 4. 加载失败只记录日志（LOG.error），不向外抛出，保证插件加载链继续。
 */
public class DexFileLoader(private val options: DexInputOptions) {

	private var zipReader: ZipReader = ZipReader()

	public fun setZipReader(zipReader: ZipReader) {
		this.zipReader = zipReader
	}

	/**
	 * 批量加载 DEX 文件。
	 *
	 * 对每个路径尝试 [loadDexFromFile]（按 DEX 文件读取，内部自动回退 zip 解包）；
	 * 单个文件失败只记录日志并跳过，不影响其他文件。
	 */
	public fun collectDexFiles(pathsList: List<Path>): List<DexReader> {
		val result = ArrayList<DexReader>()
		for (path in pathsList) {
			val file = path.toFile()
			val readers = loadDexFromFile(file)
			if (readers.isEmpty()) {
				continue
			}
			for (reader in readers) {
				LOG.debug("Loading dex: {}", reader)
				result.add(reader)
			}
		}
		return result
	}

	private fun loadDexFromFile(file: File): List<DexReader> {
		val inputStream = FileInputStream(file)
		try {
			return load(file, inputStream, file.absolutePath)
		} catch (e: Exception) {
			LOG.error("File open error: {}", file.absolutePath, e)
			return emptyList()
		} finally {
			inputStream.close()
		}
	}

	/**
	 * 从 [inputStream] 读取内容并按魔数分派：
	 * - `dex\n` → DEX 文件，直接 [loadDexReaders]；
	 * - `PK\x03\x04` 或 zip 扩展名 → [collectDexFromZip]（要求 [file] 非空，只允许顶层 zip）；
	 * - 其他 → 返回空列表（`.dex` 文件魔数不符时记录警告）。
	 */
	private fun load(file: File?, inputStream: InputStream, fileName: String): List<DexReader> {
		val inStream = if (inputStream.markSupported()) inputStream else BufferedInputStream(inputStream)
		try {
			val magic = ByteArray(DexConsts.MAX_MAGIC_SIZE)
			inStream.mark(magic.size)
			if (inStream.read(magic) != magic.size) {
				return emptyList()
			}
			if (isStartWithBytes(magic, DexConsts.DEX_FILE_MAGIC)) {
				inStream.reset()
				val content = readAllBytes(inStream)
				return loadDexReaders(fileName, content)
			}
			if (fileName.endsWith(".dex")) {
				// report invalid magic in '.dex' file
				val hex = FileUtils.bytesToHex(magic)
				val str = String(magic, StandardCharsets.US_ASCII)
				LOG.warn("Invalid DEX magic: 0x{}(\"{}\") in file: {}", hex, str, fileName)
			}
			if (file != null) {
				// allow only top level zip files
				if (isStartWithBytes(magic, DexConsts.ZIP_FILE_MAGIC) || CommonFileUtils.isZipFileExt(fileName)) {
					return collectDexFromZip(file)
				}
			}
			return emptyList()
		} finally {
			inStream.close()
		}
	}

	private fun loadFromZipEntry(content: ByteArray, fileName: String): List<DexReader> {
		if (isStartWithBytes(content, DexConsts.DEX_FILE_MAGIC) || fileName.endsWith(".dex")) {
			return loadDexReaders(fileName, content)
		}
		return emptyList()
	}

	/**
	 * 从内存中的 DEX 字节数组加载。
	 *
	 * 先按 DEX v41 header 解析（[DexHeaderV41.readIfPresent]），命中则按子 DEX 偏移拆分为多个 [DexReader]；
	 * 否则从偏移 0 加载单个 DEX。
	 */
	public fun loadDexReaders(fileName: String, content: ByteArray): List<DexReader> {
		val dexHeaderV41: DexHeaderV41? = DexHeaderV41.readIfPresent(content)
		if (dexHeaderV41 != null) {
			val readers = ArrayList<DexReader>()
			for (offset in DexHeaderV41.readSubDexOffsets(content, dexHeaderV41)) {
				readers.add(loadSingleDex(fileName, content, offset))
			}
			return readers
		}
		return listOf(loadSingleDex(fileName, content, 0))
	}

	private fun loadSingleDex(fileName: String, content: ByteArray, offset: Int): DexReader {
		if (options.isVerifyChecksum) {
			DexCheckSum.verify(fileName, content, offset)
		}
		return DexReader(nextUniqId, fileName, content, offset)
	}

	/**
	 * DEX v41 开始，单个 DEX 文件容器内可以存储多个子 DEX 结构。
	 * 请改用 [loadDexReaders]。
	 */
	@Deprecated("Use loadDexReaders instead")
	public fun loadDexReader(fileName: String, content: ByteArray): DexReader = loadSingleDex(fileName, content, 0)

	private fun collectDexFromZip(file: File): List<DexReader> {
		val result = ArrayList<DexReader>()
		val zip = zipReader.open(file)
		try {
			for (entry in zip.entries) {
				if (entry.isDirectory()) {
					continue
				}
				try {
					val readers = if (entry.preferBytes()) {
						loadFromZipEntry(entry.getBytes(), entry.getName())
					} else {
						load(null, entry.getInputStream(), entry.getName())
					}
					if (readers.isNotEmpty()) {
						result.addAll(readers)
					}
				} catch (e: Exception) {
					LOG.error("Failed to read zip entry: {}", entry, e)
				}
			}
		} catch (e: Exception) {
			LOG.error("Failed to process zip file: {}", file.absolutePath, e)
		} finally {
			zip.close()
		}
		return result
	}

	private companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DexFileLoader::class.java)

		// sharing between all instances (can be used in other plugins) // TODO:
		private var dexUniqId = 1

		@get:Synchronized
		private val nextUniqId: Int get() {
			dexUniqId++
			if (dexUniqId >= 0xFFFF) {
				dexUniqId = 1
			}
			return dexUniqId
		}

		@Synchronized
		private fun resetDexUniqId() {
			dexUniqId = 1
		}

		private fun isStartWithBytes(fileMagic: ByteArray, expectedBytes: ByteArray): Boolean {
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

		private fun readAllBytes(inStream: InputStream): ByteArray {
			val buf = ByteArrayOutputStream()
			val data = ByteArray(8192)
			while (true) {
				val read = inStream.read(data)
				if (read == -1) {
					break
				}
				buf.write(data, 0, read)
			}
			return buf.toByteArray()
		}
	}
}
