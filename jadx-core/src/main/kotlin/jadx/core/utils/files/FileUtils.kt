package jadx.core.utils.files

import jadx.core.plugins.files.IJadxFilesGetter
import jadx.core.utils.ListUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * 通用文件/IO 工具集。
 *
 * **用途**：临时目录管理、目录递归删除、流拷贝、文件名裁剪、hex/MD5 计算、zip 头判断等。
 *
 * **Kotlin 转换说明**：
 * - 全部为静态成员，用 `object` + `@JvmStatic`/`const val` 保持 Java 调用
 *   （含 `import static ...FileUtils.READ_BUFFER_SIZE`）以及 Kotlin 侧
 *   `import ...FileUtils.listFiles` 的静态导入都不变；
 * - 可空参数如实声明（如 `makeDirs(null)`、`toFile(null)` 原 Java 允许）；
 * - 递归删除的匿名 [SimpleFileVisitor] 原样保留为 `object : SimpleFileVisitor<Path>()`；
 * - `hasExtension` 用 `lowercase(Locale.getDefault())` 等价替换 Java `toLowerCase()`（同默认区域）。
 */
object FileUtils {

	private val LOG: Logger = LoggerFactory.getLogger(FileUtils::class.java)

	const val READ_BUFFER_SIZE: Int = 8 * 1024
	private const val MAX_FILENAME_LENGTH = 128
	private const val MAX_UNIQUE_ID_LENGTH = 3

	const val JADX_TMP_INSTANCE_PREFIX: String = "jadx-instance-"
	const val JADX_TMP_PREFIX: String = "jadx-tmp-"

	private var tempRootDir: Path = createTempRootDir()

	/**
	 * 在指定根目录下创建一个 jadx 实例临时目录，并设为后续 [createTempDir]/[createTempFile] 的根。
	 *
	 * 原 Java 为 `synchronized static`，Kotlin 用 `@Synchronized` 保留互斥语义。
	 */
	@JvmStatic
	@Synchronized
	fun updateTempRootDir(newTempRootDir: Path): Path {
		try {
			makeDirs(newTempRootDir)
			val dir = Files.createTempDirectory(newTempRootDir, JADX_TMP_INSTANCE_PREFIX)
			tempRootDir = dir
			dir.toFile().deleteOnExit()
			return dir
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to update temp root directory", e)
		}
	}

	private fun createTempRootDir(): Path {
		try {
			val dir = Files.createTempDirectory(JADX_TMP_INSTANCE_PREFIX)
			dir.toFile().deleteOnExit()
			return dir
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to create temp root directory", e)
		}
	}

	/** 列出目录下的直接子项。 */
	@JvmStatic
	fun listFiles(dir: Path): List<Path> {
		try {
			Files.list(dir).use { files ->
				return files.collect(Collectors.toList())
			}
		} catch (e: IOException) {
			throw JadxRuntimeException("Failed to list files in directory: $dir", e)
		}
	}

	/** 列出目录下满足 [filter] 的直接子项。 */
	@JvmStatic
	fun listFiles(dir: Path, filter: (Path) -> Boolean): List<Path> {
		try {
			Files.list(dir).use { files ->
				return files.filter { filter(it) }.collect(Collectors.toList())
			}
		} catch (e: IOException) {
			throw JadxRuntimeException("Failed to list files in directory: $dir", e)
		}
	}

	/** 把目录展开为其下的全部普通文件（跟随符号链接）；普通文件原样保留。 */
	@JvmStatic
	fun expandDirs(paths: List<Path>): List<Path> {
		val files = ArrayList<Path>(paths.size)
		for (path in paths) {
			if (Files.isDirectory(path)) {
				expandDir(path, files)
			} else {
				files.add(path)
			}
		}
		return files
	}

	private fun expandDir(dir: Path, files: MutableList<Path>) {
		try {
			Files.walk(dir, FileVisitOption.FOLLOW_LINKS).use { walk ->
				walk.filter { Files.isRegularFile(it) }.forEach { files.add(it) }
			}
		} catch (e: Exception) {
			LOG.error("Failed to list files in directory: {}", dir, e)
		}
	}

	/** 把 [source] 文件以 [entryName] 写入 zip/jar 输出流。 */
	@JvmStatic
	@Throws(IOException::class)
	fun addFileToJar(jar: JarOutputStream, source: File, entryName: String) {
		BufferedInputStream(FileInputStream(source)).use { input ->
			val entry = JarEntry(entryName)
			entry.setTime(source.lastModified())
			jar.putNextEntry(entry)

			copyStream(input, jar)
			jar.closeEntry()
		}
	}

	/** 为文件路径的父目录创建目录（路径可空）。 */
	@JvmStatic
	fun makeDirsForFile(path: Path?) {
		if (path != null) {
			makeDirs(path.toAbsolutePath().parent.toFile())
		}
	}

	@JvmStatic
	fun makeDirsForFile(file: File?) {
		if (file != null) {
			makeDirs(file.parentFile)
		}
	}

	/** 目录创建锁：避免多线程同时 mkdirs 造成竞态。 */
	private val MKDIR_SYNC: Any = Any()

	/** 递归创建目录；目录已存在且不是目录时抛出异常。 */
	@JvmStatic
	fun makeDirs(dir: File?) {
		if (dir != null) {
			synchronized(MKDIR_SYNC) {
				if (!dir.mkdirs() && !dir.isDirectory) {
					throw JadxRuntimeException("Can't create directory $dir")
				}
			}
		}
	}

	@JvmStatic
	fun makeDirs(dir: Path?) {
		if (dir != null) {
			makeDirs(dir.toFile())
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun deleteFileIfExists(filePath: Path) {
		Files.deleteIfExists(filePath)
	}

	/** 删除目录（总是返回 true，保留原 API 语义）。 */
	@JvmStatic
	fun deleteDir(dir: File): Boolean {
		deleteDir(dir.toPath())
		return true
	}

	@JvmStatic
	fun deleteDir(dir: Path) {
		deleteDir(dir, false)
	}

	@JvmStatic
	fun deleteDirIfExists(dir: Path) {
		if (Files.exists(dir)) {
			try {
				deleteDir(dir)
			} catch (e: Exception) {
				LOG.error("Failed to delete dir: {}", dir.toAbsolutePath(), e)
			}
		}
	}

	/**
	 * 递归删除目录：先并行删文件，再删空目录。
	 *
	 * @param keepRootDir 为 true 时保留根目录本身（用于“清空”而非“删除”）
	 */
	private fun deleteDir(dir: Path, keepRootDir: Boolean) {
		try {
			val files = ArrayList<Path>()
			val directories = ArrayList<Path>()
			Files.walkFileTree(
				dir,
				Collections.emptySet<FileVisitOption>(),
				Integer.MAX_VALUE,
				object : SimpleFileVisitor<Path>() {
					override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
						files.add(file)
						return FileVisitResult.CONTINUE
					}

					override fun postVisitDirectory(directory: Path, exc: IOException?): FileVisitResult {
						directories.add(directory)
						return FileVisitResult.CONTINUE
					}
				},
			)
			// 文件并行删除，提高大目录清理速度
			if (files.isNotEmpty()) {
				files.parallelStream().forEach { path ->
					try {
						Files.delete(path)
					} catch (e: Exception) {
						LOG.warn("Failed to delete file {}", path.toAbsolutePath(), e)
					}
				}
			}
			// 所有文件删完后再自底向上删空目录
			if (keepRootDir) {
				// 根目录总是最后访问，这里先移除以免被删
				ListUtils.removeLast(directories)
			}
			for (directory in directories) {
				try {
					Files.delete(directory)
				} catch (e: IOException) {
					LOG.warn("Failed to delete directory {}", directory.toAbsolutePath(), e)
				}
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to delete directory $dir", e)
		}
	}

	@JvmStatic
	fun clearTempRootDir() {
		if (Files.isDirectory(tempRootDir)) {
			clearDir(tempRootDir)
		}
	}

	/** 清空目录内容但保留目录本身。 */
	@JvmStatic
	fun clearDir(dir: Path) {
		try {
			deleteDir(dir, true)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to clear directory $dir", e)
		}
	}

	/**
	 * 已废弃。
	 * 请改用 jadx args 中的 [IJadxFilesGetter] 获取临时目录。
	 */
	@Deprecated("Migrate to IJadxFilesGetter from jadx args to get temp dir")
	@JvmStatic
	fun createTempDir(prefix: String): Path {
		try {
			val dir = Files.createTempDirectory(tempRootDir, prefix)
			dir.toFile().deleteOnExit()
			return dir
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to create temp directory with suffix: $prefix", e)
		}
	}

	/**
	 * 已废弃。
	 * 请改用 jadx args 中的 [IJadxFilesGetter] 获取临时目录。
	 */
	@Deprecated("Migrate to IJadxFilesGetter from jadx args to get temp dir")
	@JvmStatic
	fun createTempFile(suffix: String): Path {
		try {
			val path = Files.createTempFile(tempRootDir, JADX_TMP_PREFIX, suffix)
			path.toFile().deleteOnExit()
			return path
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to create temp file with suffix: $suffix", e)
		}
	}

	/**
	 * 已废弃。
	 * 请改用 jadx args 中的 [IJadxFilesGetter] 获取临时目录。
	 */
	@Deprecated("Migrate to IJadxFilesGetter from jadx args to get temp dir")
	@JvmStatic
	fun createTempFileNoDelete(suffix: String): Path {
		try {
			return Files.createTempFile(Files.createTempDirectory("jadx-persist"), "jadx-", suffix)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to create temp file with suffix: $suffix", e)
		}
	}

	/**
	 * 已废弃。
	 * 请改用 jadx args 中的 [IJadxFilesGetter] 获取临时目录。
	 */
	@Deprecated("Migrate to IJadxFilesGetter from jadx args to get temp dir")
	@JvmStatic
	fun createTempFileNonPrefixed(fileName: String): Path {
		try {
			val path = Files.createFile(tempRootDir.resolve(fileName))
			path.toFile().deleteOnExit()
			return path
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to create non-prefixed temp file: $fileName", e)
		}
	}

	/** 循环拷贝整个输入流到输出流。 */
	@JvmStatic
	@Throws(IOException::class)
	fun copyStream(input: InputStream, output: OutputStream) {
		val buffer = ByteArray(READ_BUFFER_SIZE)
		while (true) {
			val count = input.read(buffer)
			if (count == -1) {
				break
			}
			output.write(buffer, 0, count)
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun streamToByteArray(input: InputStream): ByteArray = input.readAllBytes()

	@JvmStatic
	@Throws(IOException::class)
	fun streamToString(input: InputStream): String = String(streamToByteArray(input), StandardCharsets.UTF_8)

	/** 安静地关闭资源（失败只记日志）。 */
	@JvmStatic
	fun close(c: Closeable?) {
		if (c == null) {
			return
		}
		try {
			c.close()
		} catch (e: IOException) {
			LOG.error("Close exception for {}", c, e)
		}
	}

	@JvmStatic
	@Throws(IOException::class)
	fun writeFile(file: Path, data: String) {
		makeDirsForFile(file)
		Files.writeString(
			file,
			data,
			StandardCharsets.UTF_8,
			StandardOpenOption.WRITE,
			StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING,
		)
	}

	@JvmStatic
	@Throws(IOException::class)
	fun writeFile(file: Path, data: ByteArray) {
		makeDirsForFile(file)
		Files.write(file, data, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
	}

	@JvmStatic
	@Throws(IOException::class)
	fun writeFile(file: Path, inputStream: InputStream) {
		makeDirsForFile(file)
		Files.copy(inputStream, file, StandardCopyOption.REPLACE_EXISTING)
	}

	@JvmStatic
	@Throws(IOException::class)
	fun readFile(textFile: Path): String = Files.readString(textFile)

	/** 移动/重命名文件；失败时记录日志并返回 false。 */
	@JvmStatic
	fun renameFile(sourcePath: Path, targetPath: Path): Boolean {
		try {
			Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)
			return true
		} catch (e: NoSuchFileException) {
			LOG.error("File to rename not found {}", sourcePath, e)
		} catch (e: FileAlreadyExistsException) {
			LOG.error("File with that name already exists {}", targetPath, e)
		} catch (e: IOException) {
			LOG.error("Error renaming file {}", e.message, e)
		}
		return false
	}

	/** 返回可安全写入的文件（超长文件名会被裁剪，并确保父目录存在）。 */
	@JvmStatic
	fun prepareFile(file: File): File {
		val saveFile = cutFileName(file)
		makeDirsForFile(saveFile)
		return saveFile
	}

	/** 文件名过长时截断中段，并附上原文件名的哈希前缀作为唯一标识。 */
	@JvmStatic
	fun cutFileName(file: File): File {
		var name = file.getName()
		if (name.length <= MAX_FILENAME_LENGTH) {
			return file
		}

		var uniqueID = name.hashCode().toString()
		if (uniqueID.length > MAX_UNIQUE_ID_LENGTH) {
			uniqueID = uniqueID.substring(0, MAX_UNIQUE_ID_LENGTH)
		}
		val dotIndex = name.indexOf('.')
		val lengthOfSuffix = name.length - dotIndex
		val cutAt = MAX_FILENAME_LENGTH - lengthOfSuffix - uniqueID.length - 1
		name = if (cutAt <= 0) {
			name.substring(0, MAX_FILENAME_LENGTH - 1)
		} else {
			name.substring(0, cutAt) + uniqueID + name.substring(dotIndex)
		}
		return File(file.getParentFile(), name)
	}

	private val HEX_ARRAY: ByteArray = "0123456789abcdef".toByteArray(StandardCharsets.US_ASCII)

	/** 字节数组 → 小写十六进制字符串；null/空数组返回空串。 */
	@JvmStatic
	fun bytesToHex(bytes: ByteArray?): String {
		if (bytes == null || bytes.isEmpty()) {
			return ""
		}
		val hexChars = ByteArray(bytes.size * 2)
		for (j in bytes.indices) {
			val v = bytes[j].toInt() and 0xFF
			hexChars[j * 2] = HEX_ARRAY[v ushr 4]
			hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
		}
		return String(hexChars, StandardCharsets.UTF_8)
	}

	/** 单字节 → 固定 2 位的零填充十六进制字符串。 */
	@JvmStatic
	fun byteToHex(value: Int): String {
		val v = value and 0xFF
		val hexChars = byteArrayOf(HEX_ARRAY[v ushr 4], HEX_ARRAY[v and 0x0F])
		return String(hexChars, StandardCharsets.US_ASCII)
	}

	/** int → 固定 8 位的零填充十六进制字符串（高位在前）。 */
	@JvmStatic
	fun intToHex(value: Int): String {
		val hexChars = ByteArray(8)
		var v = value
		for (i in 7 downTo 0) {
			hexChars[i] = HEX_ARRAY[v and 0x0F]
			v = v ushr 4
		}
		return String(hexChars, StandardCharsets.US_ASCII)
	}

	private val ZIP_FILE_MAGIC: ByteArray = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

	/** 通过文件头魔数 `PK\x03\x04` 判断是否为 zip。 */
	@JvmStatic
	fun isZipFile(file: File): Boolean {
		try {
			FileInputStream(file).use { input ->
				val len = ZIP_FILE_MAGIC.size
				val headers = ByteArray(len)
				val read = input.read(headers)
				return read == len && Arrays.equals(headers, ZIP_FILE_MAGIC)
			}
		} catch (e: Exception) {
			LOG.error("Failed to read zip file: {}", file.getAbsolutePath(), e)
			return false
		}
	}

	/** 去掉最后一个扩展名后的文件名（不含目录）。 */
	@JvmStatic
	fun getPathBaseName(file: Path): String {
		val fileName = file.getFileName().toString()
		val extEndIndex = fileName.lastIndexOf('.')
		if (extEndIndex == -1) {
			return fileName
		}
		return fileName.substring(0, extEndIndex)
	}

	/** 判断路径是否以给定扩展名结尾（忽略大小写，按默认区域转小写，与原 Java `toLowerCase()` 一致）。 */
	@JvmStatic
	fun hasExtension(path: Path, extension: String): Boolean {
		val fileName = path.getFileName().toString()
		return fileName.lowercase(Locale.getDefault()).endsWith(extension)
	}

	@JvmStatic
	fun toFile(path: String?): File? {
		if (path == null) {
			return null
		}
		return File(path)
	}

	@JvmStatic
	fun toPaths(files: List<File>): List<Path> = files.stream().map { it.toPath() }.collect(Collectors.toList())

	@JvmStatic
	fun toPaths(files: Array<File>): List<Path> = Stream.of(*files).map { it.toPath() }.collect(Collectors.toList())

	@JvmStatic
	fun toPathsWithTrim(files: Array<File>): List<Path> = Stream.of(*files).map { toPathWithTrim(it) }.collect(Collectors.toList())

	@JvmStatic
	fun toPathWithTrim(file: File): Path = toPathWithTrim(file.getPath())

	@JvmStatic
	fun toPathWithTrim(file: String): Path = Path.of(file.trim())

	@JvmStatic
	fun fileNamesToPaths(fileNames: List<String>): List<Path> = fileNames.stream().map { Paths.get(it) }.collect(Collectors.toList())

	@JvmStatic
	fun toFiles(paths: List<Path>): List<File> = paths.stream().map { it.toFile() }.collect(Collectors.toList())

	@JvmStatic
	fun md5Sum(str: String): String = md5Sum(str.toByteArray(StandardCharsets.UTF_8))

	@JvmStatic
	fun md5Sum(data: ByteArray): String {
		try {
			val md = MessageDigest.getInstance("MD5")
			md.update(data)
			return bytesToHex(md.digest())
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to build hash", e)
		}
	}

	/** 对输入文件的最后修改时间戳做哈希，用于判断输入是否变化。 */
	@JvmStatic
	fun buildInputsHash(inputPaths: List<Path>): String {
		try {
			ByteArrayOutputStream().use { bout ->
				DataOutputStream(bout).use { data ->
					val inputFiles = ArrayList(expandDirs(inputPaths))
					Collections.sort(inputFiles)
					data.write(inputPaths.size)
					data.write(inputFiles.size)
					for (inputFile in inputFiles) {
						val modifiedTime = Files.getLastModifiedTime(inputFile)
						data.writeLong(modifiedTime.toMillis())
					}
					return md5Sum(bout.toByteArray())
				}
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to build hash for inputs", e)
		}
	}
}
