package jadx.core.export

/**
 * Gradle 工程导出类型枚举。
 *
 * **用途**：决定导出的工程使用哪种模板（Android App / Android Library / 纯 Java）。
 * 用户在命令行或 GUI 里可以选择，未选择时由 [ExportGradle.detectExportType] 自动探测。
 *
 * **Kotlin 转换说明**：原 Java 的 `private final String desc` + `getDesc()` 改为构造参数属性
 * [desc]，JVM 上仍生成 `getDesc()`，Java 调用方无差异；`toString()` 覆写保持不变。
 */
enum class ExportGradleType(val desc: String) {
	/** 自动探测（默认值，不直接对应某种模板） */
	AUTO("Auto"),

	/** Android 应用工程（含 resources.arsc） */
	ANDROID_APP("Android App"),

	/** Android 库工程（含 classes.jar） */
	ANDROID_LIBRARY("Android Library"),

	/** 纯 Java 工程（无 Android 资源） */
	SIMPLE_JAVA("Simple Java"),
	;

	/** 返回该类型的可读描述，与原 Java `getDesc()` 一致。 */
	override fun toString(): String = desc
}
