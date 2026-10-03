package jadx.core.export

import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.core.dex.nodes.RootNode
import jadx.core.export.gen.AndroidGradleGenerator
import jadx.core.export.gen.IExportGradleGenerator
import jadx.core.export.gen.SimpleJavaGradleGenerator
import jadx.core.utils.android.AndroidManifestParser
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Gradle 工程导出的入口。
 *
 * **用途**：根据导出类型选择具体生成器（[AndroidGradleGenerator] / [SimpleJavaGradleGenerator]），
 * 先 [init] 建目录，再 [generateGradleFiles] 写文件。
 *
 * **Kotlin 转换说明**：
 * - 静态方法 [detectExportType] 放入 `companion object`，
 *   调用方 `ExportGradle.detectExportType(...)` 不变；
 * - `generator` 字段在 [init] 前为 null，如实声明为可空，用 `checkNotNull` 在调用处校验。
 */
class ExportGradle(
	private val root: RootNode,
	private val projectDir: File,
	private val resources: List<ResourceFile>,
) {

	/** 具体生成器，[init] 时创建。 */
	private var generator: IExportGradleGenerator? = null

	/** 初始化：选择生成器、建目录，并返回输出目录。 */
	fun init(): OutDirs {
		val exportType = exportGradleType
		LOG.info("Export Gradle project using '{}' template", exportType)
		val gen: IExportGradleGenerator = when (exportType) {
			ExportGradleType.ANDROID_APP,
			ExportGradleType.ANDROID_LIBRARY,
			-> AndroidGradleGenerator(root, projectDir, resources, exportType)

			ExportGradleType.SIMPLE_JAVA -> SimpleJavaGradleGenerator(root, projectDir, resources)

			else -> throw JadxRuntimeException("Unexpected export type: $exportType")
		}
		generator = gen
		gen.init()
		val outDirs = gen.outDirs
		outDirs.makeDirs()
		return outDirs
	}

	/** 生成 Gradle 文件（必须在 [init] 之后调用）。 */
	fun generateGradleFiles() {
		checkNotNull(generator) { "Generator not initialized" }.generateFiles()
	}

	/**
	 * 决定最终使用的导出类型。
	 *
	 * 规则（与原 Java 一致）：
	 * 1. 用户显式指定且不是 [ExportGradleType.AUTO]、也不等于探测结果时，以用户为准；
	 * 2. 否则采用 [detectExportType] 的探测结果。
	 */
	private val exportGradleType: ExportGradleType
		get() {
			val argsExportType = root.getArgs().exportGradleType
			val detectedType = detectExportType(root, resources)
			if (argsExportType == null ||
				argsExportType == ExportGradleType.AUTO ||
				argsExportType == detectedType
			) {
				return detectedType
			}
			return argsExportType
		}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ExportGradle::class.java)

		/**
		 * 根据资源列表探测导出类型。
		 *
		 * 有 `AndroidManifest.xml` 时：含 `classes.jar` 视为 Android Library，
		 * 含 `resources.arsc` 视为 Android App；否则为纯 Java 工程。
		 */
		fun detectExportType(root: RootNode, resources: List<ResourceFile>): ExportGradleType {
			val androidManifest = AndroidManifestParser.getAndroidManifest(resources)
			if (androidManifest != null) {
				if (resources.any { r -> r.getOriginalName() == "classes.jar" }) {
					return ExportGradleType.ANDROID_LIBRARY
				}
				if (resources.any { r -> r.getType() === ResourceType.ARSC }) {
					return ExportGradleType.ANDROID_APP
				}
			}
			return ExportGradleType.SIMPLE_JAVA
		}
	}
}
