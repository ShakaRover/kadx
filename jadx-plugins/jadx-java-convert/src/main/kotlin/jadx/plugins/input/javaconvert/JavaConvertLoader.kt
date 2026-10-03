package jadx.plugins.input.javaconvert

import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.utils.CommonFileUtils
import jadx.api.security.IJadxSecurity
import jadx.zip.ZipReader
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * 把 .jar/.aar/.class 输入转换成 dex 的加载器。
 *
 * **背景**：[process] 依次处理三类文件——jar（可能含嵌套 jar / Spring Boot 结构，
 * 需要重打包）、aar（提取内部 jar）、散落的 class 文件（先打进临时 jar）。
 * 转换后端由 [JavaConvertOptions.Mode] 决定（DX/D8/BOTH），见 [convert]。
 */
public class JavaConvertLoader(
	private val options: JavaConvertOptions,
	context: JadxPluginContext,
) {

	private val zipReader: ZipReader = context.getZipReader()
	private val security: IJadxSecurity = context.getArgs().security

	public fun process(input: java.util.List<Path>): ConvertResult {
		val result = ConvertResult()
		processJars(input, result)
		processAars(input, result)
		processClassFiles(input, result)
		return result
	}

	private fun processJars(input: java.util.List<Path>, result: ConvertResult) {
		val jarMatcher = FileSystems.getDefault().getPathMatcher("glob:**.jar")
		for (path in input) {
			if (!jarMatcher.matches(path)) {
				continue
			}
			try {
				convertJar(result, path)
			} catch (e: Exception) {
				LOG.error("Failed to convert file: {}", path.toAbsolutePath(), e)
			}
		}
	}

	private fun processClassFiles(input: java.util.List<Path>, result: ConvertResult) {
		val clsMatcher = FileSystems.getDefault().getPathMatcher("glob:**.class")
		val clsFiles = input.filter { clsMatcher.matches(it) }
		if (clsFiles.isEmpty()) {
			return
		}
		try {
			LOG.debug("Converting class files ...")
			val jarFile = Files.createTempFile("jadx-", ".jar")
			JarOutputStream(Files.newOutputStream(jarFile)).use { jo ->
				for (file in clsFiles) {
					val clsName = AsmUtils.getNameFromClassFile(file)
					if (clsName == null) {
						throw java.io.IOException("Can't read class name from file: $file")
					}
					if (!security.isValidEntryName(clsName)) {
						LOG.warn("Skip class with invalid name: {}", clsName)
						continue
					}
					addFileToJar(jo, file, clsName + ".class")
				}
			}
			result.addTempPath(jarFile)
			LOG.debug("Packed {} class files into jar: {}", clsFiles.size, jarFile)
			convertJar(result, jarFile)
		} catch (e: Exception) {
			LOG.error("Error process class files", e)
		}
	}

	private fun processAars(input: java.util.List<Path>, result: ConvertResult) {
		val aarMatcher = FileSystems.getDefault().getPathMatcher("glob:**.aar")
		for (path in input) {
			if (!aarMatcher.matches(path)) {
				continue
			}
			zipReader.readEntries(path.toFile()) { entry, inputStream ->
				try {
					val entryName = entry.getName()
					if (entryName.endsWith(".jar")) {
						val tempJar = CommonFileUtils.saveToTempFile(inputStream, ".jar")
						result.addTempPath(tempJar)
						LOG.debug("Loading jar: {} ...", entryName)
						convertJar(result, tempJar)
					}
				} catch (e: Exception) {
					LOG.error("Failed to process zip entry: {}", entry, e)
				}
			}
		}
	}

	private fun convertJar(result: ConvertResult, path: Path) {
		if (repackAndConvertJar(result, path)) {
			return
		}
		convertSimpleJar(result, path)
	}

	private fun repackAndConvertJar(result: ConvertResult, path: Path): Boolean {
		// 检查 jar 是否需要完整重打包
		val repackNeeded = zipReader.visitEntries(path.toFile()) { zipEntry ->
			val entryName = zipEntry.getName()
			if (zipEntry.isDirectory()) {
				if (entryName == "BOOT-INF/") {
					return@visitEntries true // Spring Boot jar
				}
				if (entryName == "META-INF/versions/") {
					return@visitEntries true // 排除重复类
				}
			}
			if (entryName.endsWith(".jar")) {
				return@visitEntries true // 包含子 jar
			}
			if (entryName.endsWith("module-info.class")) {
				return@visitEntries true // 需要排除 module 文件
			}
			null
		}
		if (repackNeeded != true) {
			return false
		}
		LOG.debug("Repacking jar file: {} ...", path.toAbsolutePath())
		val jarFile = Files.createTempFile("jadx-classes-", ".jar")
		result.addTempPath(jarFile)
		JarOutputStream(Files.newOutputStream(jarFile)).use { jo ->
			zipReader.readEntries(path.toFile()) { entry, inputStream ->
				try {
					val entryName = entry.getName()
					if (entryName.endsWith(".class")) {
						if (entryName.endsWith("module-info.class") || entryName.startsWith("META-INF/versions/")) {
							LOG.debug(" exclude: {}", entryName)
							return@readEntries
						}
						val clsFileContent = CommonFileUtils.loadBytes(inputStream)
						val clsName = AsmUtils.getNameFromClassFile(clsFileContent)
						if (clsName == null) {
							throw java.io.IOException("Can't read class name from file: $entryName")
						}
						if (!security.isValidEntryName(clsName)) {
							LOG.warn("Ignore class with invalid name: {} from {}", clsName, entry)
						} else {
							addJarEntry(jo, clsName + ".class", clsFileContent, null)
						}
					} else if (entryName.endsWith(".jar")) {
						val tempJar = CommonFileUtils.saveToTempFile(inputStream, ".jar")
						result.addTempPath(tempJar)
						convertJar(result, tempJar)
					}
				} catch (e: Exception) {
					LOG.error("Failed to process jar entry: {} in {}", entry, path, e)
				}
			}
		}
		convertSimpleJar(result, jarFile)
		return true
	}

	private fun convertSimpleJar(result: ConvertResult, path: Path) {
		val tempDirectory = Files.createTempDirectory("jadx-")
		result.addTempPath(tempDirectory)
		LOG.debug("Converting to dex ...")
		convert(path, tempDirectory)
		val dexFiles = collectFilesInDir(tempDirectory)
		LOG.debug("Converted {} to {} dex", path.toAbsolutePath(), dexFiles.size)
		result.addConvertedFiles(dexFiles)
	}

	private fun convert(path: Path, tempDirectory: Path) {
		// 与原 Java switch 一致：mode 为 null 时抛 NPE（正常流程中解析阶段已应用默认值 BOTH）
		when (options.getMode() ?: throw NullPointerException("mode is null")) {
			JavaConvertOptions.Mode.DX -> try {
				DxConverter.run(path, tempDirectory)
			} catch (e: Throwable) {
				LOG.error("DX convert failed, path: {}", path, e)
			}

			JavaConvertOptions.Mode.D8 -> try {
				D8Converter.run(path, tempDirectory, options)
			} catch (e: Throwable) {
				LOG.error("D8 convert failed, path: {}", path, e)
			}

			JavaConvertOptions.Mode.BOTH -> try {
				DxConverter.run(path, tempDirectory)
			} catch (e: Throwable) {
				LOG.warn("DX convert failed, trying D8, path: {}", path)
				try {
					D8Converter.run(path, tempDirectory, options)
				} catch (ex: Throwable) {
					LOG.error("D8 convert failed: {}", ex.message)
				}
			}
		}
	}

	private companion object {
		private val LOG = LoggerFactory.getLogger(JavaConvertLoader::class.java)

		private fun collectFilesInDir(tempDirectory: Path): List<Path> {
			val dexMatcher = FileSystems.getDefault().getPathMatcher("glob:**.dex")
			return Files.walk(tempDirectory, 1).use { pathStream ->
				pathStream
					.filter { p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) }
					.filter(dexMatcher::matches)
					.collect(java.util.stream.Collectors.toList())
			}
		}

		private fun addFileToJar(jar: JarOutputStream, source: Path, entryName: String) {
			val fileContent = Files.readAllBytes(source)
			val lastModifiedTime = Files.getLastModifiedTime(source, LinkOption.NOFOLLOW_LINKS)
			addJarEntry(jar, entryName, fileContent, lastModifiedTime)
		}

		private fun addJarEntry(jar: JarOutputStream, entryName: String, content: ByteArray, modTime: FileTime?) {
			val entry = JarEntry(entryName)
			if (modTime != null) {
				entry.setTime(modTime.toMillis())
			}
			jar.putNextEntry(entry)
			jar.write(content)
			jar.closeEntry()
		}
	}
}
