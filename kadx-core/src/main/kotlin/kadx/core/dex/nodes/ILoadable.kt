package kadx.core.dex.nodes

import kadx.core.utils.exceptions.DecodeException

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
