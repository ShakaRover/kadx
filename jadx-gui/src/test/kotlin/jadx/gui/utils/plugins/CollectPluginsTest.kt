package jadx.gui.utils.plugins

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.JadxPluginInfoBuilder
import jadx.api.plugins.loader.JadxPluginLoader
import jadx.cli.plugins.JadxFilesGetter
import jadx.core.utils.files.FileUtils
import jadx.gui.plugins.GuiPluginsManager
import jadx.gui.plugins.context.TestMainWindowShim
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class CollectPluginsTest {

	@Test
	fun keepSharedTempFiles() {
		val mainWindow = TestMainWindowShim.build()
		val plugin = CountingPlugin()
		val manager = object : GuiPluginsManager(mainWindow) {
			override fun buildProjectPluginLoader(): JadxPluginLoader = TestPluginLoader(plugin)
		}
		TestMainWindowShim.setPluginsManager(mainWindow, manager)

		// same temp dirs as in jadx-gui after project load
		FileUtils.updateTempRootDir(JadxFilesGetter.INSTANCE.getTempDir())
		val tempFile: Path = FileUtils.createTempFile(".tmp")

		CollectPlugins(mainWindow).build()
		assertThat(tempFile).exists()
		assertThat(plugin.initCount).isEqualTo(1)
		assertThat(plugin.unloadCount).isEqualTo(1)
	}

	private class TestPluginLoader(private val plugin: JadxPlugin) : JadxPluginLoader {
		override fun load(): List<JadxPlugin> = listOf(plugin)

		override fun close() {
			// nothing to close
		}
	}

	private class CountingPlugin : JadxPlugin {
		var initCount = 0
		var unloadCount = 0

		override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfoBuilder.pluginId("counting-plugin").name("counting").description("test").build()

		override fun init(context: JadxPluginContext) {
			initCount++
		}

		override fun unload() {
			unloadCount++
		}
	}
}
