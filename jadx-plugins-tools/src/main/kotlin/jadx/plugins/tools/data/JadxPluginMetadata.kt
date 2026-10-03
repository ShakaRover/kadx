package jadx.plugins.tools.data

import com.google.gson.annotations.SerializedName

class JadxPluginMetadata : Comparable<JadxPluginMetadata> {
	var pluginId: String? = null
	var name: String? = null
	var description: String? = null
	var homepage: String? = null
	var requiredJadxVersion: String? = null
	var version: String? = null
	var locationId: String? = null

	@SerializedName("jar")
	var path: String? = null

	var disabled: Boolean = false

	val isDisabled: Boolean get() = disabled

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is JadxPluginMetadata) return false
		return pluginId == other.pluginId
	}

	override fun hashCode(): Int = pluginId?.hashCode() ?: 0

	override fun compareTo(other: JadxPluginMetadata): Int = checkNotNull(pluginId).compareTo(checkNotNull(other.pluginId))

	override fun toString(): String = "JadxPluginMetadata{id=$pluginId, name=$name, version=${version ?: "?"}, locationId=$locationId, path=$path}"
}
