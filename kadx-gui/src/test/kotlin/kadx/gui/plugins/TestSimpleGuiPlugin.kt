package kadx.gui.plugins

import kadx.api.gui.plugins.KadxGuiContextExt
import kadx.api.gui.plugins.KadxGuiPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.KadxPluginInfoBuilder
import kadx.api.plugins.options.KadxPluginOptions
import kadx.api.plugins.options.impl.BasePluginOptionsBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 测试用普通 GUI 插件：通过 `META-INF/services/kadx.api.plugins.KadxPlugin`
 * 注册，供手工运行 kadx-gui 时验证插件加载（见 [GuiPluginsCheck]）。
 */
class TestSimpleGuiPlugin : KadxGuiPlugin() {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TestSimpleGuiPlugin::class.java)
		private const val PLUGIN_ID = "test-gui-plugin"
	}

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfoBuilder.pluginId(PLUGIN_ID)
		.name("Test simple GUI plugin")
		.description("test simple plugin")
		.build()

	override fun init(context: KadxPluginContext, guiContextExt: KadxGuiContextExt) {
		LOG.info("TestSimpleGuiPlugin init called")
	}

	override fun buildOptions(): KadxPluginOptions {
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
