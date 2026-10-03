package jadx.gui.utils

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Gson 的 [Path] 序列化/反序列化适配器（单例）。
 *
 * **做什么**：把 [Path] 写成绝对路径字符串，读回时用 [Paths.get] 还原。
 *
 * **为什么用 `object`**：原 Java 只有私有构造器 + 静态 `singleton()` 方法，
 * 用 `object` + `@JvmStatic` 保持 Java 侧 `PathTypeAdapter.singleton()` 不变。
 */
object PathTypeAdapter {

	/** 全局唯一适配器实例；`write`/`read` 均保持 Gson [TypeAdapter] 的精确签名与泛型。 */
	private val SINGLETON: TypeAdapter<Path> = object : TypeAdapter<Path>() {

		@Throws(IOException::class)
		override fun write(out: JsonWriter, value: Path?) {
			if (value == null) {
				out.nullValue()
			} else {
				out.value(value.toAbsolutePath().toString())
			}
		}

		@Throws(IOException::class)
		override fun read(reader: JsonReader): Path? {
			if (reader.peek() == JsonToken.NULL) {
				reader.nextNull()
				return null
			}
			return Paths.get(reader.nextString())
		}
	}

	fun singleton(): TypeAdapter<Path> = SINGLETON
}
