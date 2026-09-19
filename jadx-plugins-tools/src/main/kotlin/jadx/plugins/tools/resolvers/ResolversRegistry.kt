package jadx.plugins.tools.resolvers

import jadx.plugins.tools.resolvers.file.LocalFileResolver
import jadx.plugins.tools.resolvers.github.GithubReleaseResolver

object ResolversRegistry {
	private val RESOLVERS_MAP = HashMap<String, IJadxPluginResolver>()

	init {
		register(LocalFileResolver())
		register(GithubReleaseResolver())
	}

	private fun register(resolver: IJadxPluginResolver) {
		RESOLVERS_MAP[resolver.id()] = resolver
	}

	fun getResolver(locationId: String): IJadxPluginResolver {
		val sep = locationId.indexOf(':')
		if (sep <= 0) {
			throw IllegalArgumentException("Malformed locationId: $locationId")
		}
		return getById(locationId.substring(0, sep))
	}

	fun getById(resolverId: String): IJadxPluginResolver {
		val resolver = RESOLVERS_MAP[resolverId]
		if (resolver == null) {
			throw IllegalArgumentException("Unknown resolverId: $resolverId")
		}
		return resolver
	}
}
