package jadx.api.plugins.options.impl

import jadx.api.plugins.options.JadxPluginOptions
import jadx.api.plugins.options.OptionDescription

/**
 * Empty [JadxPluginOptions] implementation: accepts and ignores all options,
 * and declares no option descriptions.
 *
 * Exposed as a singleton so it can be used as a safe default in plugin base classes.
 */
class NoPluginOptions private constructor() : JadxPluginOptions {

	override fun setOptions(options: Map<String, String>) {
		// no options
	}

	override fun getOptionsDescriptions(): List<OptionDescription> = emptyList()

	companion object {
		@JvmField
		val INSTANCE: JadxPluginOptions = NoPluginOptions()
	}
}
