package jadx.core.dex.nodes

import jadx.core.utils.exceptions.DecodeException

interface ILoadable {
	/**
	 * On demand loading
	 */
	@Throws(DecodeException::class)
	fun load()

	/**
	 * Free resources
	 */
	fun unload()
}
