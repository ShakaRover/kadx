package kadx.plugins.kotlin.smap

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.plugins.kotlin.smap.pass.KotlinSourceDebugExtensionPass

class KotlinSmapPlugin : KadxPlugin {

	private val options = KotlinSmapOptions()

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfo(PLUGIN_ID, "Kotlin SMAP", "Use kotlin.SourceDebugExtension annotation for rename class alias")

	override fun init(context: KadxPluginContext) {
		context.registerOptions(options)

		if (options.isClassSourceDbg) {
			context.addPass(KotlinSourceDebugExtensionPass(options))
		}
	}

	companion object {
		const val PLUGIN_ID = "kotlin-smap"
	}
}
