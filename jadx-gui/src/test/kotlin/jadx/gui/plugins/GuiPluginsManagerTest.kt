package jadx.gui.plugins

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.gui.plugins.JadxGlobalGuiPlugin
import jadx.api.gui.plugins.JadxGuiContextExt
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.JadxPluginInfoBuilder
import jadx.api.plugins.events.JadxEvents
import jadx.api.plugins.gui.ISettingsGroup
import jadx.api.plugins.loader.JadxPluginLoader
import jadx.api.plugins.options.JadxPluginOptions
import jadx.api.plugins.options.impl.BasePluginOptionsBuilder
import jadx.core.plugins.JadxPluginManager
import jadx.gui.plugins.context.GuiPluginContext
import jadx.gui.plugins.context.TestMainWindowShim
import jadx.gui.utils.plugins.CollectPlugins
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JLabel

class GuiPluginsManagerTest {

	@Test
	fun globalPluginsLoadFailureDontBreakProject() {
		val mainWindow = TestMainWindowShim.build()
		val manager = GuiPluginsManager(mainWindow)

		val globalPlugin = object : TestPlugin("bad-global-plugin") {
			override fun pluginGlobalInit(guiContext: JadxGuiContextExt): Unit = throw RuntimeException("test")
		}
		assertThat(manager.getGlobalPluginManager().register(globalPlugin)).isNotNull()
		manager.load()

		JadxDecompiler().use { projectDecompiler ->
			assertThatCode { manager.injectGlobalPlugins(projectDecompiler) }.doesNotThrowAnyException()
		}
		assertThatCode { manager.globalPlugins }.doesNotThrowAnyException()
		assertThat(manager.globalPlugins.filter { it.isInitialized }).isEmpty()
		assertThatCode { manager.runGlobalUnload() }.doesNotThrowAnyException()
	}

	@Test
	fun failedGlobalPluginNotInjectedIntoProject() {
		val mainWindow = TestMainWindowShim.build()
		val manager = GuiPluginsManager(mainWindow)
		val failedPlugin = object : CountingGlobalPlugin("failed-plugin") {
			override fun pluginGlobalInit(guiContext: JadxGuiContextExt): Unit = throw RuntimeException("test")
		}
		val goodPlugin = CountingGlobalPlugin("good-plugin")
		manager.initGuiPluginsContextForGlobalScope()
		assertThat(manager.getGlobalPluginManager().register(failedPlugin)).isNotNull()
		assertThat(manager.getGlobalPluginManager().register(goodPlugin)).isNotNull()
		manager.runGlobalInit(manager.globalPlugins)

		assertThat(manager.globalPlugins.map { it.pluginId }).containsExactly("good-plugin")
		JadxDecompiler().use { projectDecompiler ->
			manager.initGuiPluginsContext(projectDecompiler.getPluginManager(), projectDecompiler.getArgs(), false)
			manager.injectGlobalPlugins(projectDecompiler)
			projectDecompiler.getPluginManager().initResolved(projectDecompiler)
			assertThat(projectDecompiler.getPluginManager().allPlugins.map { it.pluginId })
				.containsExactly("good-plugin")
		}
		manager.runGlobalUnload()

		assertThat(failedPlugin.projectInitCount).isZero()
		assertThat(failedPlugin.globalUnloadCount).isZero()
		assertThat(goodPlugin.projectInitCount).isEqualTo(1)
		assertThat(goodPlugin.globalUnloadCount).isEqualTo(1)
	}

	@Test
	fun globalInitErrorDontStopOtherPlugins() {
		val mainWindow = TestMainWindowShim.build()
		val manager = GuiPluginsManager(mainWindow)
		val failedPlugin = object : CountingGlobalPlugin("a-failed-plugin") {
			override fun pluginGlobalInit(guiContext: JadxGuiContextExt): Unit = throw NoClassDefFoundError("test")
		}
		val goodPlugin = CountingGlobalPlugin("b-good-plugin")
		manager.initGuiPluginsContextForGlobalScope()
		assertThat(manager.getGlobalPluginManager().register(failedPlugin)).isNotNull()
		assertThat(manager.getGlobalPluginManager().register(goodPlugin)).isNotNull()

		assertThatCode { manager.runGlobalInit(manager.globalPlugins) }.doesNotThrowAnyException()
		assertThat(goodPlugin.globalInitCount).isEqualTo(1)
		assertThat(manager.globalPlugins.map { it.pluginId }).containsExactly("b-good-plugin")
	}

	@Test
	fun loadedGlobalPluginsInjectedIntoProject() {
		val mainWindow = TestMainWindowShim.build()
		val manager = GuiPluginsManager(mainWindow)
		JadxDecompiler().use { projectDecompiler ->
			val globalPlugin = TestPlugin("global-plugin")
			assertThat(manager.getGlobalPluginManager().register(globalPlugin)).isNotNull()

			assertThat(manager.globalPlugins.map { it.pluginId }).containsExactly("global-plugin")

			manager.injectGlobalPlugins(projectDecompiler)

			assertThat(projectDecompiler.getPluginManager().allPlugins.map { it.pluginId })
				.containsExactly("global-plugin")

			// same plugin instance is used in both scopes
			val projectPlugin = projectDecompiler.getPluginManager().allPlugins.first()
			assertThat(projectPlugin.pluginInstance).isSameAs(globalPlugin)
		}
	}

	@Test
	fun globalPluginCustomSettingsUsedInProjectScope() {
		val mainWindow = TestMainWindowShim.build()
		val manager = GuiPluginsManager(mainWindow)
		JadxDecompiler().use { projectDecompiler ->
			val globalPlugin = TestPlugin("global-plugin")
			manager.initGuiPluginsContextForGlobalScope()
			val globalPluginRuntime = manager.getGlobalPluginManager().register(globalPlugin)
			assertThat(globalPluginRuntime).isNotNull()
			val globalGuiContext = checkNotNull(globalPluginRuntime).appContext?.getGuiContext() as GuiPluginContext
			val settingsGroup = TestSettingsGroup()
			globalGuiContext.settings().setCustomSettingsGroup(settingsGroup)

			manager.initGuiPluginsContext(projectDecompiler.getPluginManager(), projectDecompiler.getArgs(), false)
			manager.injectGlobalPlugins(projectDecompiler)

			val projectPlugin = projectDecompiler.getPluginManager().allPlugins.first()
			val projectGuiContext = checkNotNull(projectPlugin.appContext?.getGuiContext()) as GuiPluginContext
			assertThat(projectGuiContext.customSettingsGroup).isSameAs(settingsGroup)
		}
	}

	@Test
	fun projectPluginCustomSettingsNotAffected() {
		val mainWindow = TestMainWindowShim.build()
		val manager = GuiPluginsManager(mainWindow)
		val jadxArgs = JadxArgs()
		val globalPluginManager = JadxPluginManager(jadxArgs)
		JadxDecompiler().use { projectDecompiler ->
			manager.initGuiPluginsContext(globalPluginManager, jadxArgs, true)
			assertThat(globalPluginManager.register(TestPlugin("global-plugin"))).isNotNull()

			manager.initGuiPluginsContext(projectDecompiler.getPluginManager(), projectDecompiler.getArgs(), false)
			val projectOnly = projectDecompiler.getPluginManager().register(TestPlugin("project-plugin"))
			assertThat(projectOnly).isNotNull()
			val projectOnlyGui = checkNotNull(projectOnly).appContext?.getGuiContext() as GuiPluginContext
			val ownGroup = TestSettingsGroup()
			projectOnlyGui.settings().setCustomSettingsGroup(ownGroup)

			manager.injectGlobalPlugins(projectDecompiler)

			assertThat(projectOnlyGui.customSettingsGroup).isSameAs(ownGroup)
		}
	}

	@Test
	fun globalPluginsCollectedForSettingsWithoutProject() {
		val mainWindow = TestMainWindowShim.build()
		val projectPlugin = TestProjectPlugin("project-plugin")
		val manager = object : GuiPluginsManager(mainWindow) {
			override fun buildProjectPluginLoader(): JadxPluginLoader = TestPluginLoader(projectPlugin)
		}
		TestMainWindowShim.setPluginsManager(mainWindow, manager)

		val globalPlugin = object : TestPlugin("global-plugin") {
			override fun buildOptions(): JadxPluginOptions = TestOptions("global-plugin")
		}
		manager.initGuiPluginsContextForGlobalScope()
		assertThat(manager.getGlobalPluginManager().register(globalPlugin)).isNotNull()
		manager.runGlobalInit(manager.globalPlugins)

		val plugins = CollectPlugins(mainWindow).build()
		assertThat(plugins.map { it.pluginId }).containsExactlyInAnyOrder("global-plugin", "project-plugin")
		assertThat(plugins.filter { it.pluginId == "global-plugin" })
			.allSatisfy { p -> assertThat(p.options).isNotNull() }
	}

	@Test
	fun settingsWindowReloadedAfterGlobalInit() {
		val mainWindow = TestMainWindowShim.build()
		val manager = GuiPluginsManager(mainWindow)
		val reloaded = CountDownLatch(1)
		mainWindow.events().global().addListener(JadxEvents.RELOAD_SETTINGS_WINDOW, Consumer { reloaded.countDown() })

		assertThat(manager.getGlobalPluginManager().register(TestPlugin("global-plugin"))).isNotNull()
		manager.load()

		assertThat(reloaded.await(10, TimeUnit.SECONDS)).isTrue()
	}
}

private class TestPluginLoader(private val plugin: JadxPlugin) : JadxPluginLoader {
	override fun load(): List<JadxPlugin> = listOf(plugin)

	override fun close() {
		// nothing to close
	}
}

private class TestProjectPlugin(private val pluginId: String) : JadxPlugin {
	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfoBuilder.pluginId(pluginId).name(pluginId).description("test").build()

	override fun init(context: JadxPluginContext) {
		// no-op
	}
}

private class TestOptions(private val pluginId: String) : BasePluginOptionsBuilder() {
	override fun registerOptions() {
		strOption("$pluginId.test")
			.description("test option")
			.defaultValue("")
			.setter { }
	}
}

private class TestSettingsGroup : ISettingsGroup {
	override fun getTitle(): String = "custom"

	override fun buildComponent(): JComponent = JLabel()

	override fun getSubGroups(): List<ISettingsGroup> = emptyList()
}

private open class CountingGlobalPlugin(private val pluginId: String) : JadxGlobalGuiPlugin() {
	var globalInitCount = 0
	var projectInitCount = 0
	var globalUnloadCount = 0

	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfoBuilder.pluginId(pluginId).name(pluginId).description("test").build()

	override fun pluginGlobalInit(guiContext: JadxGuiContextExt) {
		globalInitCount++
	}

	override fun init(context: JadxPluginContext, guiContextExt: JadxGuiContextExt) {
		projectInitCount++
	}

	override fun globalUnload() {
		globalUnloadCount++
	}
}

private open class TestPlugin(private val pluginId: String) : JadxGlobalGuiPlugin() {
	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfoBuilder.pluginId(pluginId).name(pluginId).description("test").build()

	override fun pluginGlobalInit(guiContext: JadxGuiContextExt) {
		// no-op
	}

	override fun init(context: JadxPluginContext) {
		// no-op
	}
}
