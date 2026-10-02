package jadx.api.plugins

import org.jetbrains.annotations.Nullable

/**
 * 插件元信息：id、名称、描述、主页与所需 jadx 版本。
 *
 * **为什么字段与 getter 分开写**：本类是插件公共 API，Java 侧按
 * `getPluginId()` / `setHomepage(...)` 等显式方法访问，故这里保留显式
 * `fun getXxx()` 而非 Kotlin 属性（避免 Kotlin 调用点的合成属性语义变化）。
 */
class JadxPluginInfo {

	private val pluginId: String
	private val name: String
	private val description: String
	private var homepage: String

	/**
	 * 冲突的插件应声明相同的 `provides` 值；加载时只会保留其中一个。
	 */
	private var provides: String

	/**
	 * 运行该插件所需的最低 jadx 版本。
	 *
	 * 格式：`"<稳定版本>, r<非稳定版本的修订号>"`，例如 `"1.5.1, r2305"`。
	 * 详见 jadx 插件指南 wiki。
	 */
	private var requiredJadxVersion: String? = null

	constructor(id: String, name: String, description: String) : this(id, name, description, "", id)

	constructor(pluginId: String, name: String, description: String, provides: String) :
		this(pluginId, name, description, "", provides)

	constructor(pluginId: String, name: String, description: String, homepage: String, provides: String) {
		this.pluginId = pluginId
		this.name = name
		this.description = description
		this.homepage = homepage
		this.provides = provides
	}

	fun getPluginId(): String = pluginId

	fun getName(): String = name

	fun getDescription(): String = description

	fun getHomepage(): String = homepage

	fun setHomepage(homepage: String) {
		this.homepage = homepage
	}

	fun getProvides(): String = provides

	fun setProvides(provides: String) {
		this.provides = provides
	}

	@Nullable
	fun getRequiredJadxVersion(): String? = requiredJadxVersion

	fun setRequiredJadxVersion(requiredJadxVersion: String?) {
		this.requiredJadxVersion = requiredJadxVersion
	}

	override fun toString(): String = "$pluginId: $name - '$description'"
}
