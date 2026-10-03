package jadx.plugins.tools.data

class JadxPluginUpdate(
	val oldVersion: JadxPluginMetadata,
	val newVersion: JadxPluginMetadata,
) {
	val pluginId: String get() = checkNotNull(newVersion.pluginId)
	val oldVersionStr: String? get() = oldVersion.version
	val newVersionStr: String? get() = newVersion.version

	override fun toString(): String = "PluginUpdate{$pluginId: ${oldVersion.version} -> ${newVersion.version}}"
}
