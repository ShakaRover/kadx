package jadx.gui.plugins.context

import jadx.gui.JadxWrapper
import jadx.gui.events.types.JadxGuiEventsImpl
import jadx.gui.plugins.GuiPluginsManager
import jadx.gui.settings.JadxSettings
import jadx.gui.ui.MainWindow
import jadx.gui.utils.NLS
import org.junit.jupiter.api.Assumptions
import javax.swing.JMenu

/**
 * 测试用 [MainWindow] 桩：绕过重量级构造函数，用 `Unsafe.allocateInstance` 创建实例，
 * 再通过反射注入插件系统所需的字段。
 *
 * **为什么需要**：GUI 测试在无显示环境下无法真正构造 `MainWindow`（会创建 Swing 组件）。
 * 只注入 [GuiPluginsManager] 路径实际用到的字段即可。
 */
object TestMainWindowShim {

	@JvmStatic
	fun build(): MainWindow = try {
		NLS.setLocale(NLS.defaultLocale())
		val unsafeCls = Class.forName("sun.misc.Unsafe")
		val theUnsafe = unsafeCls.getDeclaredField("theUnsafe")
		theUnsafe.isAccessible = true
		val unsafe = theUnsafe.get(null)
		val mainWindow = unsafeCls.getMethod("allocateInstance", Class::class.java)
			.invoke(unsafe, MainWindow::class.java) as MainWindow

		setField(mainWindow, "pluginsMenu", JMenu("Plugins"))
		setField(mainWindow, "settings", buildSettings())
		setField(mainWindow, "events", JadxGuiEventsImpl())
		mainWindow
	} catch (e: Throwable) {
		Assumptions.abort<MainWindow>("Can't build MainWindow instance for test: $e")
	}

	@JvmStatic
	fun setPluginsManager(mainWindow: MainWindow, pluginsManager: GuiPluginsManager) {
		try {
			setField(mainWindow, "guiPluginsManager", pluginsManager)
			setField(mainWindow, "wrapper", JadxWrapper(mainWindow))
		} catch (e: Throwable) {
			Assumptions.abort<Unit>("Can't set plugins manager for test: $e")
		}
	}

	fun buildSettings(): JadxSettings {
		val settings = JadxSettings(JadxSettings.buildConfigAdapter())
		settings.loadSettingsFromJsonString("{}")
		return settings
	}

	private fun setField(target: Any, name: String, value: Any) {
		val field = target.javaClass.getDeclaredField(name)
		field.isAccessible = true
		field.set(target, value)
	}
}
