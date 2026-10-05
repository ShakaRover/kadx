package kadx.gui.plugins.context

import kadx.api.KadxDecompiler
import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.KadxPluginInfoBuilder
import kadx.gui.settings.data.ITabStatePersist
import kadx.gui.treemodel.JNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.Predicate

class GuiPluginsScopeTest {

	@Test
	fun globalEntriesSurviveProjectClose() {
		val mainWindow = TestMainWindowShim.build()
		val context = CommonGuiPluginsContext(mainWindow)
		KadxDecompiler().use { globalDecompiler ->
			registerEntries(buildContext(context, globalDecompiler, "global-plugin", true), "global")
			assertEntriesCount(context, 1)

			KadxDecompiler().use { projectDecompiler ->
				registerEntries(buildContext(context, projectDecompiler, "project-plugin", false), "project")
				assertEntriesCount(context, 2)
			}
			context.resetProjectScope()
			assertEntriesCount(context, 1)
			assertThat(context.getCodePopupActionList()).hasSize(1)
		}
	}

	@Test
	fun severalProjectsDontDuplicateOrAccumulateEntries() {
		val mainWindow = TestMainWindowShim.build()
		val context = CommonGuiPluginsContext(mainWindow)
		KadxDecompiler().use { globalDecompiler ->
			registerEntries(buildContext(context, globalDecompiler, "global-plugin", true), "global")

			for (i in 0..2) {
				KadxDecompiler().use { projectDecompiler ->
					registerEntries(buildContext(context, projectDecompiler, "project-plugin-$i", false), "project")
					assertEntriesCount(context, 2)
				}
				context.resetProjectScope()
				assertEntriesCount(context, 1)
			}
		}
	}

	@Test
	fun projectScopeResetKeepsGlobalMenuActions() {
		val mainWindow = TestMainWindowShim.build()
		val context = CommonGuiPluginsContext(mainWindow)
		KadxDecompiler().use { globalDecompiler ->
			buildContext(context, globalDecompiler, "global-plugin", true)
				.addMenuAction("global-menu") { }
			context.resetProjectScope()
			assertThat(mainWindow.getPluginsMenu().getMenuComponentCount()).isEqualTo(3)
			context.resetProjectScope()
			assertThat(mainWindow.getPluginsMenu().getMenuComponentCount()).isEqualTo(3)
		}
	}

	private fun buildContext(
		context: CommonGuiPluginsContext,
		decompiler: KadxDecompiler,
		pluginId: String,
		global: Boolean,
	): GuiPluginContext {
		val pluginRuntime = decompiler.getPluginManager().register(TestPlugin(pluginId))
		assertThat(pluginRuntime).isNotNull()
		return context.buildForPlugin(checkNotNull(pluginRuntime), global)
	}

	private fun registerEntries(guiContext: GuiPluginContext, name: String) {
		guiContext.addPopupMenuAction(name, null, null, Consumer { })
		guiContext.addTreePopupMenuEntry(name, Predicate { true }, Consumer { })
		guiContext.registerTreeInputCategory(TestInputCategory())
		guiContext.registerTabStatePersistAdapter(TestTabState())
	}

	private fun assertEntriesCount(context: CommonGuiPluginsContext, expected: Int) {
		assertThat(context.getCodePopupActionList()).hasSize(expected)
		assertThat(context.getTreePopupMenuEntries()).hasSize(expected)
		assertThat(context.getTreeInputCategories()).hasSize(expected)
		assertThat(context.getTabStatePersistAdapters()).hasSize(expected)
	}

	private class TestPlugin(private val pluginId: String) : KadxPlugin {
		override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfoBuilder.pluginId(pluginId).name(pluginId).description("test").build()

		override fun init(context: KadxPluginContext) {
			// no-op
		}
	}

	private class TestInputCategory : ITreeInputCategory {
		override fun filesFilter(file: Path): Boolean = false

		override fun buildInputNode(files: List<Path>): JNode? = null
	}

	private class TestTabState : ITabStatePersist {
		override fun getNodeClass(): Class<out JNode> = JNode::class.java

		override fun save(node: JNode): String = ""

		override fun load(stateStr: String): JNode? = null
	}
}
