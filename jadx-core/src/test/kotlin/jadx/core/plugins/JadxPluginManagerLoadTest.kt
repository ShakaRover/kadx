package jadx.core.plugins

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.JadxPluginInfoBuilder
import jadx.api.plugins.loader.JadxPluginLoader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JadxPluginManagerLoadTest {

	@TempDir
	lateinit var tmp: Path

	@Test
	fun repeatedLoadDontFail() {
		JadxDecompiler().use { decompiler ->
			val pluginManager = decompiler.getPluginManager()
			val loader = TestLoader(TestPlugin("test-plugin"))

			pluginManager.load(loader)
			assertThat(pluginManager.allPlugins).hasSize(1)

			assertThatCode { pluginManager.load(loader) }.doesNotThrowAnyException()
			assertThat(pluginManager.allPlugins).hasSize(1)
		}
	}

	// Plugins added with 'register' method (i.e global gui plugins) should survive plugins load
	@Test
	fun loadKeepRegisteredPlugins() {
		JadxDecompiler().use { decompiler ->
			val pluginManager = decompiler.getPluginManager()
			assertThat(pluginManager.register(TestPlugin("registered-plugin"))).isNotNull()

			pluginManager.load(TestLoader(TestPlugin("loaded-plugin")))

			assertThat(pluginManager.allPlugins.map { it.pluginId })
				.containsExactlyInAnyOrder("registered-plugin", "loaded-plugin")

			// and still kept after a repeated load
			pluginManager.load(TestLoader(TestPlugin("loaded-plugin")))
			assertThat(pluginManager.allPlugins.map { it.pluginId })
				.containsExactlyInAnyOrder("registered-plugin", "loaded-plugin")
		}
	}

	@Test
	fun duplicatedPluginIdRejected() {
		JadxDecompiler().use { decompiler ->
			val pluginManager = decompiler.getPluginManager()
			assertThatThrownBy {
				pluginManager.load(TestLoader(TestPlugin("same-id"), OtherTestPlugin("same-id")))
			}
				.isInstanceOf(IllegalArgumentException::class.java)
				.hasMessageContaining("Duplicate plugin id")
		}
	}

	@Test
	fun reloadPassesDontFail() {
		val smali = tmp.resolve("HelloWorld.smali")
		Files.write(smali, SMALI.toByteArray(Charsets.UTF_8))
		val args = JadxArgs()
		args.inputFiles.add(smali.toFile())
		args.outDir = tmp.resolve("out").toFile()

		JadxDecompiler(args).use { decompiler ->
			decompiler.load()
			val pluginsCount = decompiler.getPluginManager().allPlugins.size
			assertThat(pluginsCount).isPositive()

			assertThatCode { decompiler.reloadPasses() }.doesNotThrowAnyException()
			assertThat(decompiler.getPluginManager().allPlugins).hasSize(pluginsCount)

			assertThatCode { decompiler.reloadPasses() }.doesNotThrowAnyException()
			assertThat(decompiler.getPluginManager().allPlugins).hasSize(pluginsCount)
		}
	}

	private class TestLoader(vararg plugins: JadxPlugin) : JadxPluginLoader {
		private val plugins = plugins.toList()

		override fun load(): List<JadxPlugin> = ArrayList(plugins)

		override fun close() {
			// nothing to close
		}
	}

	private open class TestPlugin(private val pluginId: String) : JadxPlugin {
		override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfoBuilder.pluginId(pluginId).name(pluginId).description("test").build()

		override fun init(context: JadxPluginContext) {
			// no-op
		}
	}

	private class OtherTestPlugin(pluginId: String) : TestPlugin(pluginId)

	companion object {
		private const val SMALI = ".class Lsmali/HelloWorld;\n" +
			".super Ljava/lang/Object;\n" +
			".method constructor <init>()V\n" +
			"    .registers 1\n" +
			"    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
			"    return-void\n" +
			".end method\n"
	}
}
