package jadx.core.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.Strictness
import java.lang.reflect.Type

/**
 * Gson 构建工具。
 *
 * **用途**：统一 jadx 各模块的 JSON 序列化配置（严格模式、禁用 Unsafe、美化输出），
 * 并提供接口 <-> 实现类之间的替换适配器。
 *
 * **Kotlin 转换说明**：使用 `object` + `@JvmStatic`，保证 Java 侧
 * `GsonUtils.buildGson()` / `GsonUtils.defaultGsonBuilder()` 静态调用与静态 import 继续可用。
 */
object GsonUtils {

	@JvmStatic
	fun buildGson(): Gson = defaultGsonBuilder().create()

	@JvmStatic
	fun defaultGsonBuilder(): GsonBuilder = GsonBuilder()
		.disableJdkUnsafe()
		.disableInnerClassSerialization()
		.setStrictness(Strictness.STRICT)
		.setPrettyPrinting()

	@JvmStatic
	fun <T> interfaceReplace(replaceCls: Class<T>): InterfaceReplace<T> = InterfaceReplace(replaceCls)

	/**
	 * 接口反序列化适配器：把接口类型当作具体实现类来读写。
	 *
	 * 例如把 `ICodeComment` 接口的 JSON 反序列化成 `JadxCodeComment` 实现。
	 */
	class InterfaceReplace<T> internal constructor(private val replaceCls: Class<T>) :
		JsonSerializer<T>,
		JsonDeserializer<T> {

		@Throws(JsonParseException::class)
		override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T = context.deserialize(json, replaceCls)

		override fun serialize(src: T, typeOfSrc: Type, context: JsonSerializationContext): JsonElement = context.serialize(src, replaceCls)
	}
}
