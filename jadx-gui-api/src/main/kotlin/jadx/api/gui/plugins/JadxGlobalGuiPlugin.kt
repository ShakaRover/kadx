package jadx.api.gui.plugins

import jadx.api.plugins.JadxPluginContext

/**
 * Jadx-gui plugin of global scope:
 * - created and initialized with main window
 * - unloaded on main window destroy
 * - project init and unload events still can be used
 */
@Suppress("unused")
abstract class JadxGlobalGuiPlugin : JadxGuiPlugin() {

	/**
	 * Main init method.
	 * Will be called after main window initialization.
	 */
	abstract fun pluginGlobalInit(guiContext: JadxGuiContextExt)

	open fun globalInit(guiContext: JadxGuiContextExt) {
		guiContext.registerOptions(buildOptions())
		pluginGlobalInit(guiContext)
	}

	/**
	 * Project init method.
	 * Override to get project specific plugin context data.
	 */
	override fun init(context: JadxPluginContext, guiContextExt: JadxGuiContextExt) {
		// optional method
	}

	/**
	 * Rewrite base plugin logic to register option in global init.
	 */
	override fun init(context: JadxPluginContext) {
		val guiContext = requireNotNull(context.getGuiContext())
		init(context, guiContext as JadxGuiContextExt)
	}

	/**
	 * Project close event.
	 * Can be used to release project related data.
	 */
	override fun unload() {
		// optional method
	}

	/**
	 * Main window close event, called before window destroy.
	 * It is suggested to not start any heavy operations.
	 * Any exceptions will be ignored.
	 */
	open fun globalUnload() {
		// optional method
	}
}
