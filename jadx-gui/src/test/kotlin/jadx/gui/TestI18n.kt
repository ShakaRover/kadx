package jadx.gui

import jadx.api.plugins.utils.CommonFileUtils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.gui.utils.NLS
import org.apache.commons.lang3.StringUtils
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.HashSet
import java.util.Properties
import java.util.regex.Pattern

/**
 * 国际化（i18n）一致性测试。
 *
 * **做什么**：
 * 1. 校验各语言 `Messages_*.properties` 与默认语言逐行对齐；
 * 2. 校验代码里用到的 NLS key 与默认语言文件里的 key 完全一致。
 *
 * GUI 源码已全部迁移到 Kotlin（`src/main/kotlin`），但 `src/main/java` 仍存在，
 * 两个源码根目录都会扫描，避免漏掉 key。
 */
class TestI18n {

	@Test
	fun verifyLocales() {
		for (lang in NLS.langLocales) {
			val locale = lang.get()
			println(
				"Language: " + locale.language + " - " + locale.displayLanguage +
					", country: " + locale.country + " - " + locale.displayCountry +
					", language tag: " + locale.toLanguageTag(),
			)
		}
	}

	@Test
	@Throws(IOException::class)
	fun filesExactlyMatch() {
		val reference = Files.readAllLines(refPath)
			.stream()
			.map { getPrefix(it) }
			.collect(java.util.stream.Collectors.toList())
		Files.list(i18nPath).use { list ->
			list.filter { p -> p != refPath }
				.forEach { path -> compareToReference(path, reference) }
		}
	}

	private fun compareToReference(path: Path, reference: List<String>) {
		try {
			val lines = Files.readAllLines(path)
			for (i in reference.indices) {
				val prefix = reference[i]
				if (prefix.isEmpty()) {
					continue
				}
				if (i >= lines.size) {
					fail<Nothing>("File '" + path.fileName + "' contains unexpected lines at end")
				}
				val line = lines[i]
				if (!trimComment(line).startsWith(prefix)) {
					failLine(path, i + 1)
				}
				if (line.startsWith("#")) {
					val sep = line.indexOf('=')
					if (line.substring(sep + 1).isBlank()) {
						fail<Nothing>("File '" + path.fileName + "' has empty ref text at line " + (i + 1) + ": " + line)
					}
				}
			}
			if (lines.size != reference.size) {
				failLine(path, reference.size)
			}
		} catch (e: IOException) {
			fail<Nothing>("Process error ", e)
		}
	}

	/**
	 * All keys should be used in code and all keys in code should exist in default lang file
	 */
	@Test
	@Throws(IOException::class)
	fun keyUsage() {
		val codeKeys = collectKeysFromCode()
		val properties = Properties()
		Files.newBufferedReader(i18nPath.resolve(DEFAULT_LANG_FILE)).use { reader ->
			properties.load(reader)
		}
		val keys = HashSet<String>()
		for (keyObj in properties.keys) {
			keys.add(keyObj as String)
		}
		EXCLUDED_KEYS.forEach { keys.remove(it) }

		val errors = ArrayList<String>()
		for (codeKey in codeKeys) {
			if (!keys.contains(codeKey)) {
				errors.add("Key '$codeKey' not found in NLS strings")
			}
		}
		for (key in keys) {
			if (!codeKeys.contains(key)) {
				errors.add("Key '$key' not used in code")
			}
		}
		if (errors.isNotEmpty()) {
			fail<Nothing>("NLS key usage errors:\n " + StringUtils.join(errors, "\n "))
		}
	}

	companion object {
		private const val DEFAULT_LANG_FILE = "Messages_en_US.properties"

		private lateinit var i18nPath: Path
		private lateinit var refPath: Path
		private lateinit var guiJavaPath: Path
		private lateinit var guiKotlinPath: Path

		@BeforeAll
		@JvmStatic
		fun init() {
			i18nPath = Paths.get("src/main/resources/i18n")
			assertThat(i18nPath).exists()
			refPath = i18nPath.resolve(DEFAULT_LANG_FILE)
			assertThat(refPath).exists()
			guiJavaPath = Paths.get("src/main/java")
			assertThat(guiJavaPath).exists()
			// GUI 正在从 Java 迁移到 Kotlin，源码分散在两个源码根目录，两处都要扫描 NLS 用法
			guiKotlinPath = Paths.get("src/main/kotlin")
			assertThat(guiKotlinPath).exists()
		}

		/**
		 * Extract prefix: 'key='
		 */
		private fun getPrefix(line: String): String {
			if (line.isBlank()) {
				return ""
			}
			val sep = line.indexOf('=')
			if (sep == -1) {
				return line
			}
			if (line.startsWith("#")) {
				fail<Nothing>("$DEFAULT_LANG_FILE shouldn't contain commented values: $line")
			}
			return line.substring(0, sep + 1)
		}

		private fun trimComment(string: String): String = if (string.startsWith("#")) string.substring(1) else string

		private fun failLine(path: Path, line: Int) {
			fail<Nothing>("I18n file: " + path.fileName + " and " + DEFAULT_LANG_FILE + " differ in line " + line)
		}

		/**
		 * Temporary solution to allow use I18N strings in plugins until proper API implemented
		 */
		private val EXCLUDED_KEYS = listOf(
			// keys from `jadx-script-kotlin`
			"file.save",
			"tree.input_scripts",
			"popup.new_script",
			"popup.add_scripts",
			"script.log",
			"script.format",
			"script.check",
			// keys for GUI features ported from upstream in a later sync step (S3)
			"tree.filter",
			"popup.copy_smali_reference",
			"graph_viewer.inheritance_graph.distance",
			"graph_viewer.inheritance_graph.siblings",
		)

		private val NLS_STR_USAGE = Pattern.compile("NLS\\.str\\(\"([\\w._]*)\"[,)]")

		private fun collectKeysFromCode(): MutableSet<String> {
			val keys = HashSet<String>()
			collectKeysFromCode(guiJavaPath, keys)
			collectKeysFromCode(guiKotlinPath, keys)
			return keys
		}

		private fun collectKeysFromCode(srcPath: Path, keys: MutableSet<String>) {
			Files.walk(srcPath).use { walk ->
				walk.filter { filterCodeFiles(it) }
					.forEach { codeFile ->
						try {
							for (line in Files.readAllLines(codeFile)) {
								processCodeLine(codeFile, line, keys)
							}
						} catch (e: Exception) {
							throw JadxRuntimeException("Failed to process file: $codeFile", e)
						}
					}
			}
		}

		private fun filterCodeFiles(filePath: Path): Boolean {
			val ext = CommonFileUtils.getFileExtension(filePath.fileName.toString())
			return ext == "java" || ext == "kt"
		}

		private fun processCodeLine(p: Path, line: String, keys: MutableSet<String>) {
			if (line.contains("NLS.str(")) {
				var find = false
				val matcher = NLS_STR_USAGE.matcher(line)
				while (matcher.find()) {
					val key = matcher.group(1)
					keys.add(key)
					find = true
				}
				if (!find) {
					throw JadxRuntimeException(
						"NLS.str() should be used with constant string key, but got: " +
							line.substring(line.indexOf("NLS.str(")) +
							", file: " + p,
					)
				}
			}
			if (line.startsWith("import static jadx.gui.utils.NLS.str;")) {
				throw JadxRuntimeException("NLS.str() method import is forbidden, file: " + p)
			}
		}
	}
}
