package kadx.api.gui.plugins

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.gui.IKadxGuiPlugin
import kadx.api.plugins.gui.KadxGuiContext
import kadx.api.plugins.options.KadxPluginOptions
import kadx.api.plugins.options.impl.NoPluginOptions

/**
 * Simplified kadx-gui plugin base class.
 * Initializing only in kadx-gui and correctly build plugin options for show in preferences window.
 */
@Suppress("unused")
abstract class KadxGuiPlugin :
	KadxPlugin,
	IKadxGuiPlugin {

	/**
	 * Implement to init plugin logic.
	 *
	 * @param context       - base plugin context
	 * @param guiContextExt - extended context for kadx-gui API usage
	 */
	abstract fun init(context: KadxPluginContext, guiContextExt: KadxGuiContextExt)

	/**
	 * Optional method.
	 * Override to add plugin options into preferences.
	 */
	open fun buildOptions(): KadxPluginOptions = NoPluginOptions.INSTANCE

	override fun init(context: KadxPluginContext) {
		context.registerOptions(buildOptions())
		val guiContext: KadxGuiContext? = context.getGuiContext()
		if (guiContext is KadxGuiContextExt) {
			init(context, guiContext)
		}
	}
}
