package kadx.core.plugins

import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.KadxPluginInfoBuilder
import kadx.api.plugins.loader.KadxPluginLoader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class KadxPluginManagerLoadTest {

	@TempDir
	lateinit var tmp: Path

	@Test
	fun repeatedLoadDontFail() {
		KadxDecompiler().use { decompiler ->
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
		KadxDecompiler().use { decompiler ->
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
		KadxDecompiler().use { decompiler ->
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
		val args = KadxArgs()
		args.inputFiles.add(smali.toFile())
		args.outDir = tmp.resolve("out").toFile()

		KadxDecompiler(args).use { decompiler ->
			decompiler.load()
			val pluginsCount = decompiler.getPluginManager().allPlugins.size
			assertThat(pluginsCount).isPositive()

			assertThatCode { decompiler.reloadPasses() }.doesNotThrowAnyException()
			assertThat(decompiler.getPluginManager().allPlugins).hasSize(pluginsCount)

			assertThatCode { decompiler.reloadPasses() }.doesNotThrowAnyException()
			assertThat(decompiler.getPluginManager().allPlugins).hasSize(pluginsCount)
		}
	}

	private class TestLoader(vararg plugins: KadxPlugin) : KadxPluginLoader {
		private val plugins = plugins.toList()

		override fun load(): List<KadxPlugin> = ArrayList(plugins)

		override fun close() {
			// nothing to close
		}
	}

	private open class TestPlugin(private val pluginId: String) : KadxPlugin {
		override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfoBuilder.pluginId(pluginId).name(pluginId).description("test").build()

		override fun init(context: KadxPluginContext) {
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
