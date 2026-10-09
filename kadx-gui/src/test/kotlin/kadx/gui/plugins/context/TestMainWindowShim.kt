package kadx.gui.plugins.context

import kadx.gui.KadxWrapper
import kadx.gui.events.types.KadxGuiEventsImpl
import kadx.gui.plugins.GuiPluginsManager
import kadx.gui.settings.KadxSettings
import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
import org.junit.jupiter.api.Assumptions

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

		setField(mainWindow, "settings", buildSettings())
		setField(mainWindow, "events", KadxGuiEventsImpl())
		mainWindow
	} catch (e: Throwable) {
		Assumptions.abort<MainWindow>("Can't build MainWindow instance for test: $e")
	}

	@JvmStatic
	fun setPluginsManager(mainWindow: MainWindow, pluginsManager: GuiPluginsManager) {
		try {
			setField(mainWindow, "guiPluginsManager", pluginsManager)
			setField(mainWindow, "wrapper", KadxWrapper(mainWindow))
		} catch (e: Throwable) {
			Assumptions.abort<Unit>("Can't set plugins manager for test: $e")
		}
	}

	fun buildSettings(): KadxSettings {
		val settings = KadxSettings(KadxSettings.buildConfigAdapter())
		settings.loadSettingsFromJsonString("{}")
		return settings
	}

	private fun setField(target: Any, name: String, value: Any) {
		val field = target.javaClass.getDeclaredField(name)
		field.isAccessible = true
		field.set(target, value)
	}
}
