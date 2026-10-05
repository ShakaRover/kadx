package kadx.core.export.gen

import kadx.api.ResourceFile
import kadx.api.ResourceType
import kadx.api.security.SanitizeType
import kadx.core.dex.nodes.RootNode
import kadx.core.export.ExportGradleType
import kadx.core.export.GradleInfoStorage
import kadx.core.export.OutDirs
import kadx.core.export.TemplateFile
import kadx.core.utils.Utils
import kadx.core.utils.android.AndroidManifestParser
import kadx.core.utils.android.AppAttribute
import kadx.core.utils.android.ApplicationParams
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.xmlgen.ResContainer
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
 * 之后才使用，故用 `lateinit`；`outDirs` 保持返回非空 [OutDirs]，与原 Java 接口一致。
 */
class AndroidGradleGenerator(
	private val root: RootNode,
	private val projectDir: File,
	private val resources: List<ResourceFile>,
	exportType: ExportGradleType,
) : IExportGradleGenerator {

	/** 是否为 Android App（否则为 Android Library）。 */
	private val exportApp: Boolean = exportType == ExportGradleType.ANDROID_APP

	override lateinit var outDirs: OutDirs
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
			throw KadxRuntimeException("Gradle export failed", e)
		}
	}

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
				val arscFile = resources.firstOrNull { resourceFile -> resourceFile.getType() === ResourceType.ARSC }
				if (arscFile != null) {
					val resContainers = arscFile.loadContent().subFiles
					strings = resContainers.firstOrNull { resContainer -> resContainer.name.contains("values/strings.xml") }
						?: resContainers.firstOrNull { resContainer -> resContainer.name.contains("strings.xml") }
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
		val gradleInfo = root.gradleInfoStorage
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
		val gradleInfo: GradleInfoStorage = root.gradleInfoStorage
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
