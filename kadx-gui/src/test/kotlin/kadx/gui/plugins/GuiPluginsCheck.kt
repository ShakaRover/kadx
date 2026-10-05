package kadx.gui.plugins

import kadx.gui.KadxGUI

/**
 * Run kadx-gui with test plugins.
 */
object GuiPluginsCheck {
	@JvmStatic
	fun main(args: Array<String>) {
		KadxGUI.main(arrayOf("-v"))
	}
}
