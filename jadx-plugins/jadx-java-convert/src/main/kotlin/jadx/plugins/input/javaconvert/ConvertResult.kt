package jadx.plugins.input.javaconvert

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * 转换结果：收集转换出的 dex 文件与需要清理的临时路径。
 *
 * **背景**：实现 [java.io.Closeable]，close() 时删除所有临时文件/目录
 * （目录用 Files.walk 逆序遍历逐个删除）。由 JavaConvertPlugin.loadFiles
 * 作为 Closeable 传给 loadCodeFiles，decompiler 关闭时自动清理。
 */
public class ConvertResult : java.io.Closeable {

	private val converted = mutableListOf<Path>()
	private val tmpPaths = mutableListOf<Path>()

	public fun getConverted(): List<Path> = converted

	public fun addConvertedFiles(paths: List<Path>) {
		converted.addAll(paths)
	}

	public fun addTempPath(path: Path) {
		tmpPaths.add(path)
	}

	public fun isEmpty(): Boolean = converted.isEmpty()

	override fun close() {
		for (tmpPath in tmpPaths) {
			try {
				delete(tmpPath)
			} catch (e: Exception) {
				LOG.warn("Failed to delete temp path: {}", tmpPath, e)
			}
		}
	}

	override fun toString(): String = "ConvertResult{converted=$converted, tmpPaths=$tmpPaths}"

	private companion object {
		private val LOG = LoggerFactory.getLogger(ConvertResult::class.java)

		private fun delete(path: Path) {
			if (Files.isRegularFile(path)) {
				Files.delete(path)
				return
			}
			if (Files.isDirectory(path)) {
				Files.walk(path).use { pathStream ->
					pathStream.sorted(java.util.Comparator.reverseOrder())
						.map { it.toFile() }
						.forEach { it.delete() }
				}
			}
		}
	}
}
