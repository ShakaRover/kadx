package jadx.gui.settings.ui.cache

import jadx.api.plugins.utils.CommonFileUtils
import jadx.gui.cache.manager.CacheEntry
import java.nio.file.Paths

/**
 * 缓存表格中的一行数据：包装一个 [CacheEntry]，并额外维护显示用的项目名、占用大小与选中状态。
 */
class TableRow(private val cacheEntry: CacheEntry) {

	private val project: String = cutProjectName(cacheEntry.getProject())
	private var usage: String = "-"
	private var selected: Boolean = false

	private fun cutProjectName(project: String): String {
		if (project.startsWith("tmp:")) {
			val hashStart = project.lastIndexOf('-')
			val endIdx = if (hashStart != -1) hashStart else project.length
			return project.substring(4, endIdx) + " (Temp)"
		}
		return CommonFileUtils.removeFileExtension(Paths.get(project).fileName.toString())
	}

	fun getCacheEntry(): CacheEntry = cacheEntry

	fun getProject(): String = project

	fun getUsage(): String = usage

	fun setUsage(usage: String) {
		this.usage = usage
	}

	val isSelected: Boolean get() = selected

	fun setSelected(selected: Boolean) {
		this.selected = selected
	}
}
