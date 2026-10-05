package kadx.plugins.tools.data

class KadxInstalledPlugins {
	var version: Int = 0
	var updated: Long = 0L
	var installed: MutableList<KadxPluginMetadata> = ArrayList()
}
