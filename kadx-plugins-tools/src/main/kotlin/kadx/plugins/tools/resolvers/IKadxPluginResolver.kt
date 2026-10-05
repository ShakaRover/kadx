package kadx.plugins.tools.resolvers

import kadx.plugins.tools.data.KadxPluginMetadata

interface IKadxPluginResolver {
	/**
	 * Unique resolver identifier, should be same as locationId prefix
	 */
	fun id(): String

	/**
	 * This resolver support updates and can fetch the latest version.
	 */
	val isUpdateSupported: Boolean

	/**
	 * Fetch the latest version plugin metadata by location
	 */
	fun resolve(locationId: String): KadxPluginMetadata?

	/**
	 * Fetch several latest versions (pageable) of plugin by locationId.
	 *
	 * @param page    page number, starts with 1
	 * @param perPage result's count limit
	 */
	fun resolveVersions(locationId: String, page: Int, perPage: Int): List<KadxPluginMetadata>

	/**
	 * Check if locationId has a specified version number
	 */
	fun hasVersion(locationId: String): Boolean
}
