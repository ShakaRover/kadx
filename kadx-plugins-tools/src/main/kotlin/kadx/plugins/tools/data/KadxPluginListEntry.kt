package kadx.plugins.tools.data

data class KadxPluginListEntry(
	var pluginId: String? = null,
	var locationId: String? = null,
	var name: String? = null,
	var description: String? = null,
	var homepage: String? = null,
	var revision: Int = 0,
)
