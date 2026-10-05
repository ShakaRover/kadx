package kadx.gui.plugins

import kadx.api.gui.plugins.KadxGlobalGuiPlugin
import kadx.api.gui.plugins.KadxGuiContextExt
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.KadxPluginInfoBuilder
import kadx.api.plugins.options.KadxPluginOptions
import kadx.api.plugins.options.impl.BasePluginOptionsBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 测试用全局 GUI 插件：通过 `META-INF/services/kadx.api.plugins.KadxPlugin`
 * 注册，供手工运行 kadx-gui 时验证插件加载（见 [GuiPluginsCheck]）。
 */
class TestGlobalGuiPlugin : KadxGlobalGuiPlugin() {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TestGlobalGuiPlugin::class.java)
		private const val PLUGIN_ID = "test-gui-global-plugin"
	}

	init {
		LOG.info("TestGlobalGuiPlugin constructor called")
	}

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfoBuilder.pluginId(PLUGIN_ID)
		.name("Test global GUI plugin")
		.description("test global plugin")
		.build()

	override fun pluginGlobalInit(guiContext: KadxGuiContextExt) {
		LOG.info("TestGlobalGuiPlugin globalInit called")
	}

	override fun buildOptions(): KadxPluginOptions {
		LOG.info("TestGlobalGuiPlugin buildOptions called")
		return object : BasePluginOptionsBuilder() {
			override fun registerOptions() {
				strOption("$PLUGIN_ID.test")
					.description("global sample option")
					.defaultValue("sample")
					.setter { v -> LOG.info("TestGlobalGuiPlugin set test option to: {}", v) }
			}
		}
	}

	override fun init(context: KadxPluginContext, guiContextExt: KadxGuiContextExt) {
		LOG.info("TestGlobalGuiPlugin project init called")
	}

	override fun unload() {
		LOG.info("TestGlobalGuiPlugin project unload call")
	}

	override fun globalUnload() {
		LOG.info("TestGlobalGuiPlugin globalUnload called")
	}
}
