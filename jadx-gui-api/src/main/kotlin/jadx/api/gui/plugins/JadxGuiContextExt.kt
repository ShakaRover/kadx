package jadx.api.gui.plugins

import jadx.api.gui.IMainWindow
import jadx.api.plugins.gui.JadxGuiContext
import jadx.api.plugins.options.JadxPluginOptions

/**
 * Jadx-gui extended API.
 */
interface JadxGuiContextExt : JadxGuiContext {

	/**
	 * Access to all UI related objects and services.
	 */
	fun getMainWindow(): IMainWindow

	/**
	 * Allow to register options from global scope plugins.
	 */
	fun registerOptions(options: JadxPluginOptions)
}
