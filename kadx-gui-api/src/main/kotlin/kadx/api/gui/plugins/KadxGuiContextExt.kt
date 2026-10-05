package kadx.api.gui.plugins

import kadx.api.gui.IMainWindow
import kadx.api.plugins.gui.KadxGuiContext
import kadx.api.plugins.options.KadxPluginOptions

/**
 * Kadx-gui extended API.
 */
interface KadxGuiContextExt : KadxGuiContext {

	/**
	 * Access to all UI related objects and services.
	 */
	fun getMainWindow(): IMainWindow

	/**
	 * Allow to register options from global scope plugins.
	 */
	fun registerOptions(options: KadxPluginOptions)
}
