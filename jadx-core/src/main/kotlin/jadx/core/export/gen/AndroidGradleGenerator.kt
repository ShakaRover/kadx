package jadx.core.export.gen

import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.api.security.SanitizeType
import jadx.core.dex.nodes.RootNode
import jadx.core.export.ExportGradleType
import jadx.core.export.GradleInfoStorage
import jadx.core.export.OutDirs
import jadx.core.export.TemplateFile
import jadx.core.utils.Utils
import jadx.core.utils.android.AndroidManifestParser
import jadx.core.utils.android.AppAttribute
import jadx.core.utils.android.ApplicationParams
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.xmlgen.ResContainer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.EnumSet

/**
 * Android 工程（App 或 Library）的 Gradle 文件生成器。
 *
 * **用途**：读取 `AndroidManifest.xml` 与资源，生成 `build.gradle`、`settings.gradle`
 * 以及（必要时）`gradle.properties`。
 *
 * **Kotlin 转换说明**：`outDirs` / `baseDir` / `applicationParams` 三个字段在 [init] 里赋值、
 * 之后才使用，故用 `lateinit`；`getOutDirs()` 保持返回非空 [OutDirs]，与原 Java 接口一致。
 */
class AndroidGradleGenerator(
	private val root: RootNode,
	private val projectDir: File,
	private val resources: List<ResourceFile>,
	exportType: ExportGradleType,
) : IExportGradleGenerator {

	/** 是否为 Android App（否则为 Android Library）。 */
	private val exportApp: Boolean = exportType == ExportGradleType.ANDROID_APP

	private lateinit var outDirs: OutDirs
	private lateinit var baseDir: File
	private lateinit var applicationParams: ApplicationParams

	override fun init() {
		val moduleDir = if (exportApp) "app" else "lib"
		baseDir = File(projectDir, moduleDir)
		outDirs = OutDirs(File(baseDir, "src/main/java"), File(baseDir, "src/main"))
		applicationParams = parseApplicationParams()
	}

	override fun generateFiles() {
		try {
			saveProjectBuildGradle()
			if (exportApp) {
				saveApplicationBuildGradle()
			} else {
				saveLibraryBuildGradle()
			}
			saveSettingsGradle()
			saveGradleProperties()
		} catch (e: Exception) {
			throw JadxRuntimeException("Gradle export failed", e)
		}
	}

	override fun getOutDirs(): OutDirs = outDirs

	/** 解析 AndroidManifest.xml（可选配合 strings.xml），失败时返回空参数集合并记录警告。 */
	private fun parseApplicationParams(): ApplicationParams {
		try {
			val androidManifest = AndroidManifestParser.getAndroidManifest(resources)
			if (androidManifest == null) {
				LOG.warn("AndroidManifest.xml not found, exported files will contains 'null' fields")
				return ApplicationParams()
			}
			var strings: ResContainer? = null
			if (exportApp) {
				val arscFile = resources.stream()
					.filter { resourceFile -> resourceFile.getType() === ResourceType.ARSC }
					.findFirst().orElse(null)
				if (arscFile != null) {
					val resContainers = arscFile.loadContent().getSubFiles()
					strings = resContainers
						.stream()
						.filter { resContainer -> resContainer.getName().contains("values/strings.xml") }
						.findFirst()
						.orElseGet {
							resContainers.stream()
								.filter { resContainer -> resContainer.getName().contains("strings.xml") }
								.findFirst().orElse(null)
						}
				}
			}

			val attrs = EnumSet.noneOf(AppAttribute::class.java)
			attrs.add(AppAttribute.MIN_SDK_VERSION)
			if (exportApp) {
				attrs.add(AppAttribute.APPLICATION_LABEL)
				attrs.add(AppAttribute.TARGET_SDK_VERSION)
				attrs.add(AppAttribute.COMPILE_SDK_VERSION)
				attrs.add(AppAttribute.VERSION_NAME)
				attrs.add(AppAttribute.VERSION_CODE)
			}

			val security = root.getArgs().security
			val parser = AndroidManifestParser(androidManifest, strings, attrs, security)
			return parser.parse()
		} catch (t: Exception) {
			LOG.warn("Failed to parse AndroidManifest.xml", t)
			return ApplicationParams()
		}
	}

	@Throws(IOException::class)
	private fun saveGradleProperties() {
		val gradleInfo = root.getGradleInfoStorage()
		// Android Gradle Plugin >= 8.0.0 下，资源 ID 被当作常量表达式使用时，
		// 必须在 gradle.properties 中显式关闭 nonFinalResIds。
		if (gradleInfo.isNonFinalResIds) {
			val gradlePropertiesFile = File(projectDir, "gradle.properties")
			FileOutputStream(gradlePropertiesFile).use { fos ->
				fos.write("android.nonFinalResIds=false".toByteArray(StandardCharsets.UTF_8))
			}
		}
	}

	@Throws(IOException::class)
	private fun saveProjectBuildGradle() {
		val tmpl = loadGradleTemplate("/export/android/build.gradle.tmpl")
		tmpl.save(File(projectDir, "build.gradle"))
	}

	@Throws(IOException::class)
	private fun saveSettingsGradle() {
		val tmpl = loadGradleTemplate("/export/android/settings.gradle.tmpl")
		val appName = applicationParams.applicationLabel
		val projectName = appName ?: GradleGeneratorTools.guessProjectName(root)
		tmpl.add("projectName", projectName)
		tmpl.add("mainModuleName", baseDir.name)
		tmpl.save(File(projectDir, "settings.gradle"))
	}

	@Throws(IOException::class)
	private fun saveApplicationBuildGradle() {
		val appPackage = Utils.getOrElse(root.getAppPackage(), "UNKNOWN")
		val minSdkVersion = Utils.getOrElse(applicationParams.minSdkVersion, 0)

		val tmpl = loadGradleTemplate("/export/android/app.build.gradle.tmpl")
		tmpl.add("applicationId", appPackage)
		tmpl.add("minSdkVersion", minSdkVersion)
		tmpl.add("compileSdkVersion", applicationParams.compileSdkVersion)
		tmpl.add("targetSdkVersion", applicationParams.targetSdkVersion)
		tmpl.add("versionCode", applicationParams.versionCode)
		tmpl.add("versionName", applicationParams.versionName)
		tmpl.add("additionalOptions", genAdditionalAndroidPluginOptions(minSdkVersion))
		tmpl.save(File(baseDir, "build.gradle"))
	}

	@Throws(IOException::class)
	private fun saveLibraryBuildGradle() {
		val pkg = Utils.getOrElse(root.getAppPackage(), "UNKNOWN")
		val minSdkVersion = Utils.getOrElse(applicationParams.minSdkVersion, 0)

		val tmpl = loadGradleTemplate("/export/android/lib.build.gradle.tmpl")
		tmpl.add("packageId", pkg)
		tmpl.add("minSdkVersion", minSdkVersion)
		tmpl.add("compileSdkVersion", applicationParams.compileSdkVersion)
		tmpl.add("additionalOptions", genAdditionalAndroidPluginOptions(minSdkVersion))

		tmpl.save(File(baseDir, "build.gradle"))
	}

	@Throws(FileNotFoundException::class)
	private fun loadGradleTemplate(templatePath: String): TemplateFile {
		val tmpl = TemplateFile.fromResources(templatePath)
		val security = root.getArgs().security
		tmpl.setValueSanitizer { str -> security.sanitizeString(str, SanitizeType.GRADLE_GROOVY) }
		return tmpl
	}

	/** 根据 SDK 版本与已记录的开关，拼出额外的 Android Gradle Plugin 选项文本。 */
	private fun genAdditionalAndroidPluginOptions(minSdkVersion: Int): String {
		val additionalOptions = ArrayList<String>()
		val gradleInfo: GradleInfoStorage = root.getGradleInfoStorage()
		if ((gradleInfo.isVectorPathData && minSdkVersion < 21) || (gradleInfo.isVectorFillType && minSdkVersion < 24)) {
			additionalOptions.add("vectorDrawables.useSupportLibrary = true")
		}
		if (gradleInfo.isUseApacheHttpLegacy) {
			additionalOptions.add("useLibrary 'org.apache.http.legacy'")
		}
		val sb = StringBuilder()
		for (additionalOption in additionalOptions) {
			sb.append("        ").append(additionalOption).append('\n')
		}
		return sb.toString()
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(AndroidGradleGenerator::class.java)
	}
}
