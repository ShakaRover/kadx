package kadx.plugins.tools.resolvers

import kadx.plugins.tools.resolvers.file.LocalFileResolver
import kadx.plugins.tools.resolvers.github.GithubReleaseResolver

object ResolversRegistry {
	private val RESOLVERS_MAP = HashMap<String, IKadxPluginResolver>()

	init {
		register(LocalFileResolver())
		register(GithubReleaseResolver())
	}

	private fun register(resolver: IKadxPluginResolver) {
		RESOLVERS_MAP[resolver.id()] = resolver
	}

	fun getResolver(locationId: String): IKadxPluginResolver {
		val sep = locationId.indexOf(':')
		if (sep <= 0) {
			throw IllegalArgumentException("Malformed locationId: $locationId")
		}
		return getById(locationId.substring(0, sep))
	}

	fun getById(resolverId: String): IKadxPluginResolver {
		val resolver = RESOLVERS_MAP[resolverId]
		if (resolver == null) {
			throw IllegalArgumentException("Unknown resolverId: $resolverId")
		}
		return resolver
	}
}
