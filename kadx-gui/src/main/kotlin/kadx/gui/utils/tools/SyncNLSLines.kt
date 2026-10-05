package kadx.gui.utils.tools

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedHashMap

/**
 * i18n 文件同步工具（以英文文件为基准）。
 *
 * **做什么**：
 * - 把参考文件中新增的键以注释形式补到其他语言文件；
 * - 删除其他语言文件中参考文件不存在的键；
 * - 把其他语言文件中“注释掉的空翻译”替换为参考文本（注释形式）。
 *
 * **运行方式**：作为独立工具运行（`kadx.gui.utils.tools.SyncNLSLines`）。
 */
object SyncNLSLines {
	private val LOG: Logger = LoggerFactory.getLogger(SyncNLSLines::class.java)

	private val I18N_PATH: Path = Paths.get("src/main/resources/i18n/")
	private const val REFERENCE_FILE_NAME = "Messages_en_US.properties"

	/**
	 * 假定工具从项目根目录运行、且 kadx-gui 是直接子目录；
	 * 若 kadx-gui 本身就是项目根目录，则使用相对当前目录的路径。
	 */
	private const val GUI_MODULE_DIR_NAME = "kadx-gui"
	private val GUI_MODULE_PREFIX_PATH: Path = Paths.get(GUI_MODULE_DIR_NAME)

	@JvmStatic
	fun main(args: Array<String>) {
		try {
			process()
		} catch (e: Exception) {
			LOG.error("Failed to process i18n files", e)
		}
	}

	private fun process() {
		val refPath = getRefPath(REFERENCE_FILE_NAME)
		if (!Files.exists(refPath)) {
			LOG.error("Reference i18n file not found: {}", REFERENCE_FILE_NAME)
			return
		}
		LOG.info("Using reference file: {}", refPath.toAbsolutePath())
		val i18nDir = refPath.toAbsolutePath().getParent()
		if (i18nDir == null) {
			LOG.error("Could not determine i18n directory from reference path: {}", refPath)
			return
		}
		val refFileLines = Files.readAllLines(refPath, StandardCharsets.UTF_8)
		Files.list(i18nDir).use { pathStream ->
			pathStream.filter { path ->
				val fileName = path.getFileName().toString()
				fileName != REFERENCE_FILE_NAME &&
					fileName.startsWith("Messages_") &&
					fileName.endsWith(".properties")
			}.forEach { targetPath ->
				try {
					LOG.info("Processing target file: {}", targetPath.toAbsolutePath())
					applySync(refFileLines, targetPath)
				} catch (e: Exception) {
					LOG.error("Failed to sync file: {}", targetPath, e)
				}
			}
		}
		LOG.info("I18N synchronization process finished.")
	}

	/**
	 * 把 properties 行解析为“键 → 完整行”的映射；注释与空行（键为 null）不存入。
	 */
	private fun parseProperties(lines: List<String>): MutableMap<String, String> {
		val properties = LinkedHashMap<String, String>()
		for (line in lines) {
			val key = extractKey(line)
			if (key != null) {
				properties[key] = line
			}
		}
		return properties
	}

	/**
	 * 从 properties 行中提取键；注释、空行或非键值行返回 `null`。
	 */
	private fun extractKey(line: String): String? {
		val trimmedLine = javaTrim(line)
		if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
			return null
		}
		val separatorIndex = trimmedLine.indexOf('=')
		if (separatorIndex == -1) {
			return trimmedLine
		}
		return javaTrim(trimmedLine.substring(0, separatorIndex))
	}

	@Throws(IOException::class)
	private fun applySync(refFileLines: List<String>, targetPath: Path) {
		val originalTargetLines = Files.readAllLines(targetPath, StandardCharsets.UTF_8)
		val targetProperties = parseProperties(originalTargetLines)
		val newTargetLines = ArrayList<String>(refFileLines.size)
		var updated = false
		for (refLine in refFileLines) {
			val refKey = extractKey(refLine)
			if (refKey == null) {
				// 参考文件中的注释或空行，原样保留
				newTargetLines.add(refLine)
			} else if (targetProperties.containsKey(refKey)) {
				val targetLine = targetProperties.getValue(refKey)
				val trimmed = javaTrim(targetLine)
				if (trimmed.startsWith("#") &&
					javaTrim(trimmed.substring(1)).startsWith(refKey) &&
					trimmed.endsWith("=")
				) {
					// 目标为“注释掉的空翻译”，使用参考文本（注释形式）
					newTargetLines.add('#' + javaTrim(refLine))
				} else {
					newTargetLines.add(targetLine)
				}
			} else {
				// 参考文件中的新键，以注释形式补入
				newTargetLines.add('#' + javaTrim(refLine))
			}
		}
		// 判断内容是否有变化
		if (originalTargetLines.size != newTargetLines.size) {
			updated = true
		} else {
			for (i in originalTargetLines.indices) {
				if (originalTargetLines[i] != newTargetLines[i]) {
					updated = true
					break
				}
			}
		}
		if (updated) {
			LOG.info(
				"Updating {} ({} lines -> {} lines)",
				targetPath.getFileName(),
				originalTargetLines.size,
				newTargetLines.size,
			)
			Files.write(targetPath, newTargetLines, StandardCharsets.UTF_8)
		} else {
			LOG.info("No changes needed for {}", targetPath.getFileName())
		}
	}

	private fun getRefPath(referenceFileName: String): Path {
		// 相对项目根目录（存在 src/main/resources 的目录）
		val projectRootRelative = I18N_PATH.resolve(referenceFileName)
		if (Files.exists(projectRootRelative)) {
			return projectRootRelative.toAbsolutePath()
		}
		// 相对模块目录（如 kadx-gui/src/main/resources）
		val moduleRelative = GUI_MODULE_PREFIX_PATH.resolve(I18N_PATH).resolve(referenceFileName)
		if (Files.exists(moduleRelative)) {
			return moduleRelative.toAbsolutePath()
		}
		// 从 GUI_MODULE_DIR_NAME 内部运行时的路径
		val currentDirRelative = Paths.get(".").resolve(I18N_PATH).resolve(referenceFileName)
		if (Files.exists(currentDirRelative)) {
			return currentDirRelative.toAbsolutePath()
		}
		throw RuntimeException("Can't find reference I18N: $referenceFileName")
	}

	/** 复刻 Java `String.trim()` 语义（仅去除 ≤ U+0020 的字符）。 */
	private fun javaTrim(s: String): String = s.trim { it <= ' ' }
}
