package jadx.cli.config

import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import jadx.commons.app.JadxCommonFiles
import jadx.core.utils.GsonUtils
import jadx.core.utils.exceptions.JadxArgsValidateException
import jadx.core.utils.exceptions.JadxRuntimeException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

/**
 * jadx 配置文件的 Gson 适配器：负责把 [IJadxConfig] 对象读写为 `.json`。
 *
 * **做什么**：解析配置引用（默认名 / 短名 / 完整路径），用 Gson 反序列化与序列化配置对象，
 * 并跳过标注了 [JadxConfigExclude] 的字段。
 *
 * **为什么这样写**：公共 API（jadx-cli / jadx-gui 共用），构造器与 `load`/`save` 等方法签名
 * 原样保留；Gson 的排除策略放进 `companion object`，`configPath`/`defaultConfigFileName`
 * 用 Kotlin 属性暴露同名 getter，Java 调用方零改动。
 */
class JadxConfigAdapter<T : IJadxConfig> {

	private val configCls: Class<T>
	val defaultConfigFileName: String
	private val gson: Gson

	/** 当前使用的配置文件路径；在调用 [useConfigRef] 之前为 null。 */
	var configPath: Path? = null
		private set

	constructor(configCls: Class<T>, defaultConfigName: String) : this(configCls, defaultConfigName, Consumer { })

	constructor(configCls: Class<T>, defaultConfigName: String, applyGsonOptions: Consumer<GsonBuilder>) {
		this.configCls = configCls
		this.defaultConfigFileName = defaultConfigName + ".json"
		val gsonBuilder = GsonUtils.defaultGsonBuilder()
		gsonBuilder.setExclusionStrategies(GSON_EXCLUSION_STRATEGY)
		applyGsonOptions.accept(gsonBuilder)
		this.gson = gsonBuilder.create()
	}

	/** 使用配置引用（文件名/路径/短名）解析并记录配置文件路径。 */
	fun useConfigRef(configRef: String?) {
		this.configPath = resolveConfigRef(configRef)
	}

	/** 从配置文件加载；文件不存在时返回 null。 */
	fun load(): T? {
		if (!Files.isRegularFile(configPath)) {
			// 文件不存在
			return null
		}
		try {
			gson.newJsonReader(Files.newBufferedReader(configPath)).use { reader: JsonReader ->
				return gson.fromJson(reader, configCls)
			}
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to load config file: $configPath", e)
		}
	}

	/** 把配置对象写入配置文件。 */
	fun save(configObject: T) {
		try {
			val jsonStr = gson.toJson(configObject, configCls)
			// 不使用流式写出：序列化中途失败会破坏原配置文件
			Files.writeString(configPath, jsonStr)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to save config file: $configPath", e)
		}
	}

	fun objectToJsonString(configObject: T): String = gson.toJson(configObject, configCls)

	fun jsonStringToObject(jsonStr: String): T = gson.fromJson(jsonStr, configCls)

	private fun resolveConfigRef(configRef: String?): Path {
		if (configRef == null || configRef.isEmpty()) {
			// 使用默认配置文件
			return JadxCommonFiles.getConfigDir().resolve(defaultConfigFileName)
		}
		if (configRef.contains("/") || configRef.contains("\\")) {
			if (!configRef.lowercase().endsWith(".json")) {
				throw JadxArgsValidateException("Config file extension should be '.json'")
			}
			return Path.of(configRef)
		}
		// 视为短名
		return JadxCommonFiles.getConfigDir().resolve("$configRef.json")
	}

	companion object {
		/** Gson 排除策略：跳过带 [JadxConfigExclude] 的字段。 */
		private val GSON_EXCLUSION_STRATEGY: ExclusionStrategy = object : ExclusionStrategy {
			override fun shouldSkipField(f: FieldAttributes): Boolean = f.getAnnotation(JadxConfigExclude::class.java) != null

			override fun shouldSkipClass(clazz: Class<*>): Boolean = false
		}
	}
}
