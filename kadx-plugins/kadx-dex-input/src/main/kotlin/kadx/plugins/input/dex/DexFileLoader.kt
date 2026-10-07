package kadx.plugins.input.dex

import kadx.api.plugins.utils.CommonFileUtils
import kadx.core.Kadx
import kadx.core.utils.files.FileUtils
import kadx.plugins.input.dex.sections.DexConsts
import kadx.plugins.input.dex.sections.DexHeaderV41
import kadx.plugins.input.dex.utils.DexCheckSum
import kadx.zip.IZipEntry
import kadx.zip.ZipReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
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

	/** S3-A：mmap 路径的临时目录（由 [DexInputPlugin] 从 kadx args 注入）。为 null 时退回堆内路径。 */
	private var tempDir: Path? = null

	/** 是否启用 mmap 路径（非 Windows 默认开，可用 `KADX_DEX_MMAP` 覆盖）。 */
	private val mmapEnabled: Boolean = DexMmap.isEnabled()

	/** 注入临时目录；未注入时 mmap 路径自动退回堆内路径。 */
	public fun setTempDir(tempDir: Path) {
		this.tempDir = tempDir
	}

	/** S3-B：跨会话持久缓存根目录（`<cacheDir>/dex-cache`），由 [DexInputPlugin] 注入。 */
	private var cacheDir: Path? = null

	/** 本次运行使用的持久缓存；null 表示不落盘（只用会话临时目录）。 */
	private var diskCache: DexDiskCache? = null

	/** 注入缓存目录；未注入或初始化失败时自动退回会话临时目录。 */
	public fun setCacheDir(cacheDir: Path) {
		this.cacheDir = cacheDir
	}

	/**
	 * 批量加载 DEX 文件。
	 *
	 * 对每个路径尝试 [loadDexFromFile]（按 DEX 文件读取，内部自动回退 zip 解包）；
	 * 单个文件失败只记录日志并跳过，不影响其他文件。
	 */
	public fun collectDexFiles(pathsList: List<Path>): List<DexReader> {
		val result = ArrayList<DexReader>()
		diskCache = buildDiskCache(pathsList)
		try {
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
		} finally {
			diskCache?.logSummary()
			DexMmap.logSummary(tempDir?.resolve(DexMmap.SUB_DIR))
			diskCache = null
		}
		return result
	}

	/**
	 * 构建跨会话的 dex 解压持久缓存（S3-B）。
	 *
	 * 版本键 = `格式版本:kadx.version:inputsHash`，inputsHash 用 **size + mtime**
	 * （[FileUtils.buildInputsHashWithSize]），不用既有 mtime-only 版本。
	 * 拿不到缓存目录或任何异常都只降级为「不落盘」，不影响反编译。
	 */
	private fun buildDiskCache(pathsList: List<Path>): DexDiskCache? {
		if (!mmapEnabled) {
			return null
		}
		val root = cacheDir
		if (root == null) {
			LOG.warn("Dex mmap is enabled but no cache dir was injected, using session temp dir")
			return null
		}
		return try {
			val inputsHash = FileUtils.buildInputsHashWithSize(pathsList)
			val versionKey = "$DEX_CACHE_FORMAT_VERSION:${Kadx.version}:$inputsHash"
			DexDiskCache(root.resolve(DexDiskCache.SUB_DIR), versionKey)
		} catch (e: Exception) {
			LOG.warn("Failed to init dex disk cache, continuing without it", e)
			null
		}
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
				if (mmapEnabled && file != null) {
					// 已经是磁盘文件：直接映射，省掉一次 readAllBytes
					try {
						val buffer = DexMmap.mapFile(file)
						DexMmap.logSummary(file.parentFile?.toPath() ?: file.toPath())
						return loadDexReaders(fileName, buffer, 0)
					} catch (e: Exception) {
						LOG.warn("Failed to mmap dex file '{}', falling back to heap buffer", fileName, e)
					}
				}
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
	 * mmap 路径（S3-A）：先只读 4 字节判断魔数，**是 dex 才**流式落盘 + 映射。
	 *
	 * 这顺带消掉了一个旧路径的浪费：原实现对**每个** zip 条目都调 `entry.bytes`
	 * （全量 inflate）只为检查魔数；现在非 dex 条目只读 4 字节。
	 *
	 * 非 dex 条目返回空列表，与 [loadFromZipEntry] 的行为一致（无需回退）。
	 */
	private fun loadFromZipEntryMmap(entry: IZipEntry, mmapDir: Path, inputLabel: String): List<DexReader> {
		// 1) 先只读 4 字节判魔数（非 dex 直接返回，避免为每个条目全量 inflate）
		if (!isDexEntry(entry)) {
			return emptyList()
		}
		// 2) 取得一个离堆 buffer（缓存命中 -> 写缓存 -> 会话临时文件 -> 堆内兜底）
		val buffer = obtainDexBuffer(entry, mmapDir, inputLabel)
		// 3) 解析放在 try/catch **之外**：条目本身解析失败（例如 packer 放的假 dex，
		//    带 dex\n 魔数但头部是垃圾）必须按解析错误上报，不能被误报成「写缓存失败」
		//    并白白重试三次；这也与改动前的行为一致（由调用方的 per-entry catch 处理）。
		return loadDexReaders(entry.name, buffer, 0)
	}

	/** 只读 4 字节判断该条目是否为 dex（或名字以 .dex 结尾，保持原行为）。 */
	private fun isDexEntry(entry: IZipEntry): Boolean {
		val stream = BufferedInputStream(entry.inputStream)
		return try {
			stream.mark(DexConsts.MAX_MAGIC_SIZE)
			val magic = ByteArray(DexConsts.MAX_MAGIC_SIZE)
			val read = stream.read(magic)
			stream.reset()
			(read == DexConsts.MAX_MAGIC_SIZE && isStartWithBytes(magic, DexConsts.DEX_FILE_MAGIC)) ||
				entry.name.endsWith(".dex")
		} catch (e: Exception) {
			LOG.warn("Failed to probe dex entry '{}', skipping", entry.name, e)
			false
		} finally {
			stream.close()
		}
	}

	/**
	 * 取得一个 dex 数据源：优先缓存命中（直接映射，跳过解压），否则解压落盘后映射；
	 * 落盘/映射全部失败时兜底回堆内字节。
	 *
	 * 注意：本方法只负责「拿到数据」，**不解析** —— 解析失败不是这里的错误。
	 */
	private fun obtainDexBuffer(entry: IZipEntry, mmapDir: Path, inputLabel: String): ByteBuffer {
		val cache = diskCache
		if (cache != null) {
			val hit = cache.lookup(inputLabel, entry.name)
			if (hit != null) {
				return DexMmap.mapFile(hit.toFile())
			}
			try {
				return DexMmap.mapFile(cache.store(inputLabel, entry.name, entry.inputStream).toFile())
			} catch (e: Exception) {
				LOG.warn("Failed to write dex cache for '{}', falling back to temp file", entry.name, e)
			}
		}
		try {
			return DexMmap.mapFile(DexMmap.writeStreamToFile(entry.inputStream, mmapDir, entry.name))
		} catch (e: Exception) {
			// 兜底：mmap 全部失败（磁盘满 / 映射限制 / 平台不支持）时回退到原有堆内字节路径
			LOG.warn("Failed to mmap dex entry '{}', falling back to heap buffer", entry.name, e)
		}
		return ByteBuffer.wrap(entry.bytes)
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
	 * 从 [ByteBuffer] 加载（mmap 路径）。语义与 [loadDexReaders] 的 ByteArray 版本一致。
	 */
	public fun loadDexReaders(fileName: String, buf: ByteBuffer, offset: Int): List<DexReader> {
		val dexHeaderV41: DexHeaderV41? = DexHeaderV41.readIfPresent(buf)
		if (dexHeaderV41 != null) {
			val readers = ArrayList<DexReader>()
			for (subOffset in DexHeaderV41.readSubDexOffsets(buf, dexHeaderV41)) {
				readers.add(loadSingleDex(fileName, buf, subOffset))
			}
			return readers
		}
		return listOf(loadSingleDex(fileName, buf, offset))
	}

	private fun loadSingleDex(fileName: String, buf: ByteBuffer, offset: Int): DexReader {
		if (options.isVerifyChecksum) {
			DexCheckSum.verify(fileName, buf, offset)
		}
		return DexReader(nextUniqId, fileName, buf, offset)
	}

	/**
	 * DEX v41 开始，单个 DEX 文件容器内可以存储多个子 DEX 结构。
	 * 请改用 [loadDexReaders]。
	 */
	@Deprecated("Use loadDexReaders instead")
	public fun loadDexReader(fileName: String, content: ByteArray): DexReader = loadSingleDex(fileName, content, 0)

	private fun collectDexFromZip(file: File): List<DexReader> {
		val result = ArrayList<DexReader>()
		val mmapDir: Path? = if (mmapEnabled) {
			val dir = tempDir
			if (dir == null) {
				LOG.warn("Dex mmap is enabled but no temp dir was injected, using heap buffers")
			}
			dir?.resolve(DexMmap.SUB_DIR)
		} else {
			null
		}
		val zip = zipReader.open(file)
		try {
			for (entry in zip.entries) {
				if (entry.isDirectory) {
					continue
				}
				try {
					val readers = if (mmapDir != null) {
						loadFromZipEntryMmap(entry, mmapDir, file.name)
					} else if (entry.preferBytes()) {
						loadFromZipEntry(entry.bytes, entry.name)
					} else {
						load(null, entry.inputStream, entry.name)
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
		// 汇总日志统一在 collectDexFiles 的 finally 里打（同时覆盖 .dex 直读路径）
		return result
	}

	private companion object {
		private val LOG: Logger = LoggerFactory.getLogger(DexFileLoader::class.java)

		/** dex 持久缓存的数据格式版本；字节布局或命名规则变化时必须递增。 */
		private const val DEX_CACHE_FORMAT_VERSION = 1

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
