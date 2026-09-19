package jadx.plugins.tools.data

class JadxInstalledPlugins {
	var version: Int = 0
	var updated: Long = 0L
	var installed: MutableList<JadxPluginMetadata> = ArrayList()
}
