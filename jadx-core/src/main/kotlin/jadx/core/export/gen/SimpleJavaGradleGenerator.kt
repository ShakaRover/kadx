package jadx.core.export.gen

import jadx.api.ResourceFile
import jadx.api.security.SanitizeType
import jadx.core.dex.nodes.RootNode
import jadx.core.export.OutDirs
import jadx.core.export.TemplateFile
import jadx.core.utils.exceptions.JadxRuntimeException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * 纯 Java 工程的 Gradle 文件生成器。
 *
 * **用途**：生成 `settings.gradle.kts` 与 `app/build.gradle.kts`，输出到
 * `app/src/main/java` 与 `app/src/main/resources`。
 *
 * **Kotlin 转换说明**：`outDirs` / `appDir` 在 [init] 里赋值、之后才使用，故用 `lateinit`；
 * `outDirs` 保持返回非空 [OutDirs]，与原 Java 接口一致。
 */
class SimpleJavaGradleGenerator(
	private val root: RootNode,
	private val projectDir: File,
	@Suppress("unused") private val resources: List<ResourceFile>,
) : IExportGradleGenerator {

	override lateinit var outDirs: OutDirs
	private lateinit var appDir: File

	override fun init() {
		appDir = File(projectDir, "app")
		val srcOutDir = File(appDir, "src/main/java")
		val resOutDir = File(appDir, "src/main/resources")
		outDirs = OutDirs(srcOutDir, resOutDir)
	}

	override fun generateFiles() {
		try {
			saveSettingsGradle()
			saveBuildGradle()
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to generate gradle files", e)
		}
	}

	@Throws(IOException::class)
	private fun saveSettingsGradle() {
		val tmpl = loadGradleTemplate("/export/java/settings.gradle.kts.tmpl")
		tmpl.add("projectName", GradleGeneratorTools.guessProjectName(root))
		tmpl.save(File(projectDir, "settings.gradle.kts"))
	}

	@Throws(IOException::class)
	private fun saveBuildGradle() {
		val tmpl = loadGradleTemplate("/export/java/build.gradle.kts.tmpl")
		tmpl.save(File(appDir, "build.gradle.kts"))
	}

	@Throws(FileNotFoundException::class)
	private fun loadGradleTemplate(templatePath: String): TemplateFile {
		val tmpl = TemplateFile.fromResources(templatePath)
		val security = root.getArgs().security
		tmpl.setValueSanitizer { str -> security.sanitizeString(str, SanitizeType.GRADLE_KOTLIN) }
		return tmpl
	}
}
