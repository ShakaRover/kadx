package kadx.plugins.tools.resolvers.file

import kadx.plugins.tools.data.KadxPluginMetadata
import kadx.plugins.tools.resolvers.IKadxPluginResolver
import kadx.plugins.tools.utils.PluginUtils.removePrefix
import java.io.File

class LocalFileResolver : IKadxPluginResolver {
	override fun id(): String = "file"

	override val isUpdateSupported: Boolean = false

	private fun isValidFileLocation(locationId: String): Boolean = locationId.startsWith("file:") && (locationId.endsWith(".jar") || locationId.endsWith(".zip"))

	override fun resolve(locationId: String): KadxPluginMetadata? {
		if (!isValidFileLocation(locationId)) {
			return null
		}
		val pluginFile = File(removePrefix(locationId, "file:"))
		if (!pluginFile.isFile) {
			throw RuntimeException("File not found: ${pluginFile.absolutePath}")
		}
		val metadata = KadxPluginMetadata().apply {
			this.locationId = locationId
			this.path = pluginFile.absolutePath
		}
		return metadata
	}

	override fun resolveVersions(locationId: String, page: Int, perPage: Int): List<KadxPluginMetadata> {
		if (page > 1) {
			return emptyList()
		}
		return resolve(locationId)?.let { listOf(it) } ?: emptyList()
	}

	override fun hasVersion(locationId: String): Boolean = false
}
