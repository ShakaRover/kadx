package jadx.gui.plugins

import jadx.api.gui.plugins.JadxGuiContextExt
import jadx.api.gui.plugins.JadxGuiPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.JadxPluginInfoBuilder
import jadx.api.plugins.options.JadxPluginOptions
import jadx.api.plugins.options.impl.BasePluginOptionsBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 测试用普通 GUI 插件：通过 `META-INF/services/jadx.api.plugins.JadxPlugin`
 * 注册，供手工运行 jadx-gui 时验证插件加载（见 [GuiPluginsCheck]）。
 */
class TestSimpleGuiPlugin : JadxGuiPlugin() {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TestSimpleGuiPlugin::class.java)
		private const val PLUGIN_ID = "test-gui-plugin"
	}

	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfoBuilder.pluginId(PLUGIN_ID)
		.name("Test simple GUI plugin")
		.description("test simple plugin")
		.build()

	override fun init(context: JadxPluginContext, guiContextExt: JadxGuiContextExt) {
		LOG.info("TestSimpleGuiPlugin init called")
	}

	override fun buildOptions(): JadxPluginOptions {
		LOG.info("TestSimpleGuiPlugin buildOptions called")
		return object : BasePluginOptionsBuilder() {
			override fun registerOptions() {
				strOption("$PLUGIN_ID.test")
					.description("sample option")
					.defaultValue("test")
					.setter { v -> LOG.info("TestSimpleGuiPlugin set test option to: {}", v) }
			}
		}
	}

	override fun unload() {
		LOG.info("TestSimpleGuiPlugin unload called")
	}
}
