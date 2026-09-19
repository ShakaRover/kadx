package jadx.plugins.tools.resolvers

import jadx.plugins.tools.data.JadxPluginMetadata
import java.util.Optional

interface IJadxPluginResolver {
	/**
	 * Unique resolver identifier, should be same as locationId prefix
	 */
	fun id(): String

	/**
	 * This resolver support updates and can fetch the latest version.
	 */
	fun isUpdateSupported(): Boolean

	/**
	 * Fetch the latest version plugin metadata by location
	 */
	fun resolve(locationId: String): Optional<JadxPluginMetadata>

	/**
	 * Fetch several latest versions (pageable) of plugin by locationId.
	 *
	 * @param page    page number, starts with 1
	 * @param perPage result's count limit
	 */
	fun resolveVersions(locationId: String, page: Int, perPage: Int): List<JadxPluginMetadata>

	/**
	 * Check if locationId has a specified version number
	 */
	fun hasVersion(locationId: String): Boolean
}
