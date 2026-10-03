package jadx.core.utils.android

import jadx.core.utils.exceptions.JadxRuntimeException
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.HashMap
import java.util.TreeMap

/**
 * 文本格式资源映射文件读写工具（`xxxxxxxx=name` 每行一条）。
 *
 * **用途**：内置 `res-map.txt` 以及 CLI 工具导出的自定义映射文件都使用该格式。
 *
 * **Kotlin 转换说明**：全部为静态方法，用 `object` + `@JvmStatic` 保持 Java 调用不变。
 */
object TextResMapFile {

	/** 每行前 8 个字符是 16 进制资源 id，第 9 个字符是分隔符 `=`。 */
	private const val SPLIT_POS = 8

	/** 从输入流读取映射（UTF-8）。 */
	fun read(`is`: InputStream): Map<Int, String> {
		try {
			return BufferedReader(InputStreamReader(`is`, StandardCharsets.UTF_8)).use { br ->
				val resMap = HashMap<Int, String>()
				while (true) {
					val line = br.readLine() ?: break
					parseLine(resMap, line)
				}
				resMap
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to read res-map file", e)
		}
	}

	private fun parseLine(resMap: MutableMap<Int, String>, line: String) {
		val id = line.substring(0, SPLIT_POS).toInt(16)
		val name = line.substring(SPLIT_POS + 1)
		resMap[id] = name
	}

	/** 从文件路径读取映射。 */
	fun read(resMapFile: Path): Map<Int, String> {
		try {
			return Files.newInputStream(resMapFile).use { input -> read(input) }
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to read res-map file", e)
		}
	}

	/** 按资源 id 升序写出映射（每行 `%08x=name`）。 */
	fun write(resMapFile: Path, inputResMap: Map<Int, String>) {
		try {
			val resMap = TreeMap(inputResMap)
			val lines = ArrayList<String>(resMap.size)
			for ((key, value) in resMap) {
				lines.add(String.format("%08x=%s", key, value))
			}
			Files.write(resMapFile, lines, StandardCharsets.UTF_8)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to write res-map file", e)
		}
	}
}
