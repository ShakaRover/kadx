package kadx.plugins.input.apkm

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.plugins.input.dex.DexInputPlugin

class ApkmInputPlugin : KadxPlugin {

	override fun getPluginInfo() = KadxPluginInfo(
		"apkm-input",
		"APKM Input",
		"Load .apkm files",
	)

	override fun init(context: KadxPluginContext) {
		val dexInputPlugin = context.plugins().getInstance(DexInputPlugin::class.java)
		context.addCodeInput(ApkmCustomCodeInput(dexInputPlugin, context.getZipReader()))
		context.getDecompiler().addCustomResourcesLoader(ApkmCustomResourcesLoader(context.getZipReader()))
	}
}
