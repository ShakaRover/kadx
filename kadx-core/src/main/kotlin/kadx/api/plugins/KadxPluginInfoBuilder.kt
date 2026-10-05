package kadx.api.plugins

import kadx.core.plugins.versions.VerifyRequiredVersion
import java.util.Objects

/**
 * [KadxPluginInfo] 的流式构建器。
 *
 * **用法**：`KadxPluginInfoBuilder.pluginId("my-plugin").name("My Plugin").description("...").build()`。
 *
 * **为什么保留 `pluginId` 静态工厂**：Java 插件普遍以 `KadxPluginInfoBuilder.pluginId(...)`
 * 的静态形式启动构建，Kotlin 侧用 `companion object` + `@JvmStatic` 生成同样的静态方法。
 */
class KadxPluginInfoBuilder private constructor() {

	private var pluginId: String? = null
	private var name: String? = null
	private var description: String? = null
	private var homepage: String = ""
	private var requiredKadxVersion: String? = null
	private var provides: String? = null

	/** 设置插件名。参数为 null 会抛 NPE（与原 Java 的 `Objects.requireNonNull` 一致）。 */
	fun name(name: String): KadxPluginInfoBuilder {
		this.name = Objects.requireNonNull(name)
		return this
	}

	/** 设置插件描述。参数为 null 会抛 NPE。 */
	fun description(description: String): KadxPluginInfoBuilder {
		this.description = Objects.requireNonNull(description)
		return this
	}

	/** 设置插件主页（可为空字符串，不校验）。 */
	fun homepage(homepage: String): KadxPluginInfoBuilder {
		this.homepage = homepage
		return this
	}

	/** 设置该插件「提供」的能力标识，用于解决插件冲突（相同标识只会加载一个）。 */
	fun provides(provides: String): KadxPluginInfoBuilder {
		this.provides = provides
		return this
	}

	/** 设置运行该插件所需的最低 kadx 版本（构建时校验）。 */
	fun requiredKadxVersion(versions: String): KadxPluginInfoBuilder {
		this.requiredKadxVersion = versions
		return this
	}

	/**
	 * 完成构建。
	 * 会校验 pluginId / name / description 必填，provides 缺省时回退为 pluginId。
	 */
	fun build(): KadxPluginInfo {
		val id = pluginId ?: throw NullPointerException("PluginId is required")
		val pluginName = name ?: throw NullPointerException("Name is required")
		val pluginDesc = description ?: throw NullPointerException("Description is required")
		if (provides == null) {
			provides = id
		}
		val version = requiredKadxVersion
		if (version != null) {
			VerifyRequiredVersion.verify(version)
		}
		val pluginInfo = KadxPluginInfo(id, pluginName, pluginDesc, homepage, checkNotNull(provides))
		pluginInfo.setRequiredKadxVersion(version)
		return pluginInfo
	}

	companion object {
		/** 构建入口：指定必填的插件 id。 */
		@JvmStatic
		fun pluginId(pluginId: String): KadxPluginInfoBuilder {
			val builder = KadxPluginInfoBuilder()
			builder.pluginId = Objects.requireNonNull(pluginId)
			return builder
		}
	}
}
