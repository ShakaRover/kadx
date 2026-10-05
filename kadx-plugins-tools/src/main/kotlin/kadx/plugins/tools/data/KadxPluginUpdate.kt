package kadx.plugins.tools.data

class KadxPluginUpdate(
	val oldVersion: KadxPluginMetadata,
	val newVersion: KadxPluginMetadata,
) {
	val pluginId: String get() = checkNotNull(newVersion.pluginId)
	val oldVersionStr: String? get() = oldVersion.version
	val newVersionStr: String? get() = newVersion.version

	override fun toString(): String = "PluginUpdate{$pluginId: ${oldVersion.version} -> ${newVersion.version}}"
}
