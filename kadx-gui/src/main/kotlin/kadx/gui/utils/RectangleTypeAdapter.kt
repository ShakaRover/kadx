package kadx.gui.utils

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.awt.Rectangle
import java.io.IOException

/**
 * Gson 的 [Rectangle] 序列化/反序列化适配器（单例）。
 *
 * **做什么**：把 [Rectangle] 写成 `{x, y, width, height}` 的 JSON 对象，读回时还原。
 *
 * **为什么用 `object`**：原 Java 只有私有构造器 + 静态 `singleton()` 方法，
 * 用 `object` + `@JvmStatic` 保持 Java 侧 `RectangleTypeAdapter.singleton()` 不变。
 */
object RectangleTypeAdapter {

	/** 全局唯一适配器实例；`write`/`read` 保持 Gson [TypeAdapter] 的精确签名与泛型。 */
	private val SINGLETON: TypeAdapter<Rectangle> = object : TypeAdapter<Rectangle>() {

		@Throws(IOException::class)
		override fun write(out: JsonWriter, value: Rectangle?) {
			if (value == null) {
				out.nullValue()
			} else {
				out.beginObject()
				out.name("x").value(value.x)
				out.name("y").value(value.y)
				out.name("width").value(value.width)
				out.name("height").value(value.height)
				out.endObject()
			}
		}

		@Throws(IOException::class)
		override fun read(reader: JsonReader): Rectangle? {
			if (reader.peek() == JsonToken.NULL) {
				reader.nextNull()
				return null
			}
			reader.beginObject()
			val rectangle = Rectangle()
			while (reader.hasNext()) {
				val name = reader.nextName()
				when (name) {
					"x" -> rectangle.x = reader.nextInt()
					"y" -> rectangle.y = reader.nextInt()
					"width" -> rectangle.width = reader.nextInt()
					"height" -> rectangle.height = reader.nextInt()
					else -> throw IllegalArgumentException("Unknown field in Rectangle: $name")
				}
			}
			reader.endObject()
			return rectangle
		}
	}

	fun singleton(): TypeAdapter<Rectangle> = SINGLETON
}
