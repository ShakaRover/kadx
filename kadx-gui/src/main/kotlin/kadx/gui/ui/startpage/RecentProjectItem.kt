package kadx.gui.ui.startpage

import kadx.api.plugins.utils.CommonFileUtils
import java.nio.file.Path
import java.util.Objects

/**
 * “最近打开的项目”列表中的一项。
 *
 * **做什么**：包装一个项目文件路径，提供项目名（去掉扩展名的文件名）与绝对路径；
 * 按路径实现值相等（[equals]/[hashCode]），以便在列表中判重。
 *
 * **为什么不用 `data class`**：保持原有“按路径判等”的语义与 `toString`（仅返回项目名）。
 */
class RecentProjectItem(private val path: Path) {

	fun getPath(): Path = path

	val projectName: String get() = CommonFileUtils.removeFileExtension(path.fileName.toString())

	val absolutePath: String get() = path.toAbsolutePath().toString()

	override fun toString(): String = projectName

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o == null || javaClass != o.javaClass) {
			return false
		}
		val that = o as RecentProjectItem
		return Objects.equals(path, that.path)
	}

	override fun hashCode(): Int = Objects.hash(path)
}
