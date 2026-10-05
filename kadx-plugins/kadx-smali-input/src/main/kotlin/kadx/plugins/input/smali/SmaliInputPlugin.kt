package kadx.plugins.input.smali

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.input.data.impl.EmptyCodeLoader
import kadx.plugins.input.dex.DexInputPlugin
import java.nio.file.Path

/**
 * Smali 输入插件：注册 .smali 文件的加载能力。
 *
 * **背景**：init() 时注册选项、同步线程数，并注册代码输入——把 .smali 文件经
 * [SmaliConvert] 汇编成 dex 数据后委托给 DexInputPlugin 解析。
 */
public class SmaliInputPlugin : KadxPlugin {

	private val options = SmaliInputOptions()

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfo(PLUGIN_ID, "Smali Input", "Load .smali files")

	override fun init(context: KadxPluginContext) {
		context.registerOptions(options)
		options.threads = context.getArgs().threadsCount

		val dexInput: DexInputPlugin = context.plugins().getInstance(DexInputPlugin::class.java)
		// 注意：KadxCodeInput 是 Kotlin 接口，Kotlin lambda 不能做 SAM 转换（仅 Java 接口可以），需用 object 表达式
		context.addCodeInput(object : KadxCodeInput {
			override fun loadFiles(input: java.util.List<Path>): ICodeLoader {
				val convert = SmaliConvert()
				if (!convert.execute(input, options)) {
					return EmptyCodeLoader.INSTANCE
				}
				return dexInput.loadDexData(convert.dexData)
			}
		})
	}

	public companion object {
		public const val PLUGIN_ID = "smali-input"
	}
}
