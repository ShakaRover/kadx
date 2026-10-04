package jadx.gui.plugins

import jadx.api.gui.plugins.JadxGlobalGuiPlugin
import jadx.api.gui.plugins.JadxGuiContextExt
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.JadxPluginInfoBuilder
import jadx.api.plugins.options.JadxPluginOptions
import jadx.api.plugins.options.impl.BasePluginOptionsBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 测试用全局 GUI 插件：通过 `META-INF/services/jadx.api.plugins.JadxPlugin`
 * 注册，供手工运行 jadx-gui 时验证插件加载（见 [GuiPluginsCheck]）。
 */
class TestGlobalGuiPlugin : JadxGlobalGuiPlugin() {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TestGlobalGuiPlugin::class.java)
		private const val PLUGIN_ID = "test-gui-global-plugin"
	}

	init {
		LOG.info("TestGlobalGuiPlugin constructor called")
	}

	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfoBuilder.pluginId(PLUGIN_ID)
		.name("Test global GUI plugin")
		.description("test global plugin")
		.build()

	override fun pluginGlobalInit(guiContext: JadxGuiContextExt) {
		LOG.info("TestGlobalGuiPlugin globalInit called")
	}

	override fun buildOptions(): JadxPluginOptions {
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

	override fun init(context: JadxPluginContext, guiContextExt: JadxGuiContextExt) {
		LOG.info("TestGlobalGuiPlugin project init called")
	}

	override fun unload() {
		LOG.info("TestGlobalGuiPlugin project unload call")
	}

	override fun globalUnload() {
		LOG.info("TestGlobalGuiPlugin globalUnload called")
	}
}
