package kadx.plugins.input.apks

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.plugins.input.dex.DexInputPlugin

class ApksInputPlugin : KadxPlugin {
	override fun getPluginInfo() = KadxPluginInfo(
		"apks-input",
		"APKS Input",
		"Load .apks files",
	)

	override fun init(context: KadxPluginContext) {
		val dexInputPlugin = context.plugins().getInstance(DexInputPlugin::class.java)
		context.addCodeInput(ApksCustomCodeInput(dexInputPlugin, context.getZipReader()))
		context.getDecompiler().addCustomResourcesLoader(ApksCustomResourcesLoader(context.getZipReader()))
	}
}
