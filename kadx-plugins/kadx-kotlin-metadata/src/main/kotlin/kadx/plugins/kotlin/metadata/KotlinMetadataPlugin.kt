package kadx.plugins.kotlin.metadata

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.plugins.kotlin.metadata.pass.KotlinMetadataDecompilePass
import kadx.plugins.kotlin.metadata.pass.KotlinMetadataPreparePass

class KotlinMetadataPlugin : KadxPlugin {

	private val options = KotlinMetadataOptions()

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfo(PLUGIN_ID, "Kotlin Metadata", "Use kotlin.Metadata annotation for code generation")

	override fun init(context: KadxPluginContext) {
		context.registerOptions(options)
		if (options.isPreparePassNeeded) {
			context.addPass(KotlinMetadataPreparePass(options))
		}
		if (options.isDecompilePassNeeded) {
			context.addPass(KotlinMetadataDecompilePass(options))
		}
	}

	companion object {
		const val PLUGIN_ID = "kotlin-metadata"
	}
}
