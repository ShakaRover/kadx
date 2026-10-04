package jadx.gui.plugins

import jadx.gui.JadxGUI

/**
 * Run jadx-gui with test plugins.
 */
object GuiPluginsCheck {
	@JvmStatic
	fun main(args: Array<String>) {
		JadxGUI.main(arrayOf("-v"))
	}
}
