package kadx.api.plugins.options.impl

import kadx.api.plugins.options.KadxPluginOptions
import kadx.api.plugins.options.OptionDescription

/**
 * Empty [KadxPluginOptions] implementation: accepts and ignores all options,
 * and declares no option descriptions.
 *
 * Exposed as a singleton so it can be used as a safe default in plugin base classes.
 */
class NoPluginOptions private constructor() : KadxPluginOptions {

	override fun setOptions(options: Map<String, String>) {
		// no options
	}

	override fun getOptionsDescriptions(): List<OptionDescription> = emptyList()

	companion object {
		@JvmField
		val INSTANCE: KadxPluginOptions = NoPluginOptions()
	}
}
