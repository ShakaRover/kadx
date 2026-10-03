package jadx.gui.ui.export

import jadx.core.export.ExportGradleType

/**
 * 导出工程对话框的结果数据。
 *
 * **做什么**：保存用户在“导出工程”对话框中选择的选项（是否跳过源码/资源、
 * 是否导出为 Gradle 工程、Gradle 类型、导出路径），供导出任务读取。
 *
 * **为什么保留显式 getter/setter 函数**：Java 调用方（`MainWindow` 里的导出回调）
 * 按 `isSkipSources()` / `getExportPath()` 等函数名访问，保留函数可做到零改动。
 */
class ExportProjectProperties {
	private var skipSources: Boolean = false
	private var skipResources: Boolean = false
	private var asGradleMode: Boolean = false
	private var gradleType: ExportGradleType? = null
	private var path: String? = null

	val isSkipSources: Boolean get() = skipSources

	fun setSkipSources(skipSources: Boolean) {
		this.skipSources = skipSources
	}

	val isSkipResources: Boolean get() = skipResources

	fun setSkipResources(skipResources: Boolean) {
		this.skipResources = skipResources
	}

	val isAsGradleMode: Boolean get() = asGradleMode

	fun setAsGradleMode(asGradleMode: Boolean) {
		this.asGradleMode = asGradleMode
	}

	val exportGradleType: ExportGradleType? get() = gradleType

	fun setExportGradleType(exportGradleType: ExportGradleType?) {
		this.gradleType = exportGradleType
	}

	val exportPath: String? get() = path

	fun setExportPath(exportPath: String) {
		this.path = exportPath
	}

	override fun toString(): String = "ExportProjectProperties{exportPath='" + path + '\'' +
		", asGradleMode=" + asGradleMode +
		", exportGradleType=" + gradleType +
		", skipSources=" + skipSources +
		", skipResources=" + skipResources +
		'}'
}
