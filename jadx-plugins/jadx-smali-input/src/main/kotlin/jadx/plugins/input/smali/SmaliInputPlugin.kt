package jadx.plugins.input.smali

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.input.data.impl.EmptyCodeLoader
import jadx.plugins.input.dex.DexInputPlugin
import java.nio.file.Path

/**
 * Smali 输入插件：注册 .smali 文件的加载能力。
 *
 * **背景**：init() 时注册选项、同步线程数，并注册代码输入——把 .smali 文件经
 * [SmaliConvert] 汇编成 dex 数据后委托给 DexInputPlugin 解析。
 */
public class SmaliInputPlugin : JadxPlugin {

	private val options = SmaliInputOptions()

	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfo(PLUGIN_ID, "Smali Input", "Load .smali files")

	override fun init(context: JadxPluginContext) {
		context.registerOptions(options)
		options.threads = context.getArgs().threadsCount

		val dexInput: DexInputPlugin = context.plugins().getInstance(DexInputPlugin::class.java)
		// 注意：JadxCodeInput 是 Kotlin 接口，Kotlin lambda 不能做 SAM 转换（仅 Java 接口可以），需用 object 表达式
		context.addCodeInput(object : JadxCodeInput {
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
