package jadx.plugins.tools.resolvers.file

import jadx.plugins.tools.data.JadxPluginMetadata
import jadx.plugins.tools.resolvers.IJadxPluginResolver
import jadx.plugins.tools.utils.PluginUtils.removePrefix
import java.io.File
import java.util.Optional

class LocalFileResolver : IJadxPluginResolver {
	override fun id(): String = "file"

	override fun isUpdateSupported(): Boolean = false

	private fun isValidFileLocation(locationId: String): Boolean = locationId.startsWith("file:") && (locationId.endsWith(".jar") || locationId.endsWith(".zip"))

	override fun resolve(locationId: String): Optional<JadxPluginMetadata> {
		if (!isValidFileLocation(locationId)) {
			return Optional.empty()
		}
		val pluginFile = File(removePrefix(locationId, "file:"))
		if (!pluginFile.isFile) {
			throw RuntimeException("File not found: ${pluginFile.absolutePath}")
		}
		val metadata = JadxPluginMetadata().apply {
			this.locationId = locationId
			this.path = pluginFile.absolutePath
		}
		return Optional.of(metadata)
	}

	override fun resolveVersions(locationId: String, page: Int, perPage: Int): List<JadxPluginMetadata> {
		if (page > 1) {
			return emptyList()
		}
		return resolve(locationId).map { listOf(it) }.orElseGet { emptyList() }
	}

	override fun hasVersion(locationId: String): Boolean = false
}
