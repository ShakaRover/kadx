package jadx.gui.plugins

import jadx.api.JadxDecompiler
import jadx.api.gui.plugins.JadxGlobalGuiPlugin
import jadx.api.gui.plugins.JadxGuiContextExt
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.JadxPluginInfoBuilder
import jadx.gui.plugins.context.TestMainWindowShim
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GlobalGuiPluginLifecycleTest {

	@Test
	fun lifecycleAcrossSeveralProjects() {
		val mainWindow = TestMainWindowShim.build()
		val manager = GuiPluginsManager(mainWindow)
		val plugin = CountingPlugin()

		manager.initGuiPluginsContextForGlobalScope()
		assertThat(manager.getGlobalPluginManager().register(plugin)).isNotNull()
		manager.runGlobalInit(manager.globalPlugins)

		assertThat(plugin.globalInitCount).isEqualTo(1)
		assertThat(plugin.projectInitCount).isEqualTo(0)
		assertThat(plugin.unloadCount).isEqualTo(0)
		assertThat(plugin.globalUnloadCount).isEqualTo(0)

		// open and close two projects
		for (i in 1..2) {
			JadxDecompiler().use { projectDecompiler ->
				manager.initGuiPluginsContext(projectDecompiler.getPluginManager(), projectDecompiler.getArgs(), false)
				manager.injectGlobalPlugins(projectDecompiler)
				projectDecompiler.getPluginManager().initResolved(projectDecompiler)

				assertThat(plugin.projectInitCount).isEqualTo(i)
				assertThat(projectDecompiler.getPluginManager().allPlugins.first().pluginInstance).isSameAs(plugin)
			}
			assertThat(plugin.unloadCount).isEqualTo(i)
			assertThat(plugin.globalInitCount).isEqualTo(1)
			assertThat(plugin.globalUnloadCount).isEqualTo(0)
		}

		// jadx-gui exit
		manager.runGlobalUnload()
		assertThat(plugin.globalUnloadCount).isEqualTo(1)
		assertThat(plugin.globalInitCount).isEqualTo(1)
		assertThat(plugin.projectInitCount).isEqualTo(2)
		assertThat(plugin.unloadCount).isEqualTo(2)
	}

	private class CountingPlugin : JadxGlobalGuiPlugin() {
		var globalInitCount = 0
		var projectInitCount = 0
		var unloadCount = 0
		var globalUnloadCount = 0

		override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfoBuilder.pluginId("counting-global-plugin")
			.name("counting").description("test").build()

		override fun pluginGlobalInit(guiContext: JadxGuiContextExt) {
			globalInitCount++
		}

		override fun init(context: JadxPluginContext, guiContextExt: JadxGuiContextExt) {
			projectInitCount++
		}

		override fun unload() {
			unloadCount++
		}

		override fun globalUnload() {
			globalUnloadCount++
		}
	}
}
