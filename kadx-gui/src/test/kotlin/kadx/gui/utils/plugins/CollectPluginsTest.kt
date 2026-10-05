package kadx.gui.utils.plugins

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.KadxPluginInfoBuilder
import kadx.api.plugins.loader.KadxPluginLoader
import kadx.cli.plugins.KadxFilesGetter
import kadx.core.utils.files.FileUtils
import kadx.gui.plugins.GuiPluginsManager
import kadx.gui.plugins.context.TestMainWindowShim
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class CollectPluginsTest {

	@Test
	fun keepSharedTempFiles() {
		val mainWindow = TestMainWindowShim.build()
		val plugin = CountingPlugin()
		val manager = object : GuiPluginsManager(mainWindow) {
			override fun buildProjectPluginLoader(): KadxPluginLoader = TestPluginLoader(plugin)
		}
		TestMainWindowShim.setPluginsManager(mainWindow, manager)

		// same temp dirs as in kadx-gui after project load
		FileUtils.updateTempRootDir(KadxFilesGetter.INSTANCE.getTempDir())
		val tempFile: Path = FileUtils.createTempFile(".tmp")

		CollectPlugins(mainWindow).build()
		assertThat(tempFile).exists()
		assertThat(plugin.initCount).isEqualTo(1)
		assertThat(plugin.unloadCount).isEqualTo(1)
	}

	private class TestPluginLoader(private val plugin: KadxPlugin) : KadxPluginLoader {
		override fun load(): List<KadxPlugin> = listOf(plugin)

		override fun close() {
			// nothing to close
		}
	}

	private class CountingPlugin : KadxPlugin {
		var initCount = 0
		var unloadCount = 0

		override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfoBuilder.pluginId("counting-plugin").name("counting").description("test").build()

		override fun init(context: KadxPluginContext) {
			initCount++
		}

		override fun unload() {
			unloadCount++
		}
	}
}
