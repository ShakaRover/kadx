package jadx.gui.utils

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Gson 的 [Path] 适配器：把路径相对 [basePath] 存储。
 *
 * **做什么**：写出时把绝对路径转成相对于 [basePath] 的相对路径（跨机器迁移工程更友好），
 * 读取时再拼回绝对路径。无法相对化时退化为绝对路径。
 */
class RelativePathTypeAdapter(private val basePath: Path) : TypeAdapter<Path>() {

	@Throws(IOException::class)
	override fun write(out: JsonWriter, value: Path?) {
		if (value == null) {
			out.nullValue()
		} else {
			val absPath = value.toAbsolutePath().normalize()
			val resultPath = try {
				basePath.relativize(absPath)
			} catch (e: IllegalArgumentException) {
				LOG.warn("Unable to build a relative path to {} - using absolute path", absPath)
				absPath
			}
			out.value(resultPath.toString())
		}
	}

	@Throws(IOException::class)
	override fun read(reader: JsonReader): Path? {
		if (reader.peek() == JsonToken.NULL) {
			reader.nextNull()
			return null
		}
		val path = Paths.get(reader.nextString())
		if (path.isAbsolute) {
			return path
		}
		return basePath.resolve(path)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(RelativePathTypeAdapter::class.java)
	}
}
