package jadx.api.gui.plugins

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.gui.IJadxGuiPlugin
import jadx.api.plugins.gui.JadxGuiContext
import jadx.api.plugins.options.JadxPluginOptions
import jadx.api.plugins.options.impl.NoPluginOptions

/**
 * Simplified jadx-gui plugin base class.
 * Initializing only in jadx-gui and correctly build plugin options for show in preferences window.
 */
@Suppress("unused")
abstract class JadxGuiPlugin :
	JadxPlugin,
	IJadxGuiPlugin {

	/**
	 * Implement to init plugin logic.
	 *
	 * @param context       - base plugin context
	 * @param guiContextExt - extended context for jadx-gui API usage
	 */
	abstract fun init(context: JadxPluginContext, guiContextExt: JadxGuiContextExt)

	/**
	 * Optional method.
	 * Override to add plugin options into preferences.
	 */
	open fun buildOptions(): JadxPluginOptions = NoPluginOptions.INSTANCE

	override fun init(context: JadxPluginContext) {
		context.registerOptions(buildOptions())
		val guiContext: JadxGuiContext? = context.getGuiContext()
		if (guiContext is JadxGuiContextExt) {
			init(context, guiContext)
		}
	}
}
