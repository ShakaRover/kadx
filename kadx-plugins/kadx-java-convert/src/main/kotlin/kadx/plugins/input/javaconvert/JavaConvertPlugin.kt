package kadx.plugins.input.javaconvert

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.KadxPluginInfoBuilder
import kadx.api.plugins.data.KadxPluginRuntimeData
import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.input.data.impl.EmptyCodeLoader
import kadx.plugins.input.dex.DexInputPlugin
import java.nio.file.Path

/**
 * java-convert 插件：把 .class/.jar/.aar 文件转成 dex。
 *
 * **背景**：同时实现 KadxPlugin 与 KadxCodeInput——init() 注册选项并创建
 * [JavaConvertLoader]，loadFiles() 对输入做转换（结果作为 Closeable 传给
 * loadCodeFiles，decompiler 关闭时自动清理临时文件）。
 */
public class JavaConvertPlugin :
	KadxPlugin,
	KadxCodeInput {

	private val options = JavaConvertOptions()

	/** @Nullable init() 之前为 null */
	private var dexInput: KadxPluginRuntimeData? = null
	private var loader: JavaConvertLoader? = null

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfoBuilder.pluginId(PLUGIN_ID)
		.name("Java Convert")
		.description("Convert .class, .jar and .aar files to dex")
		.provides("java-input")
		.build()

	override fun init(context: KadxPluginContext) {
		context.registerOptions(options)
		dexInput = context.plugins().getById(DexInputPlugin.PLUGIN_ID)
		val newLoader = JavaConvertLoader(options, context)
		loader = newLoader
		context.addCodeInput(this)
	}

	override fun loadFiles(input: java.util.List<Path>): ICodeLoader {
		val result = checkNotNull(loader).process(input)
		if (result.isEmpty) {
			result.close()
			return EmptyCodeLoader.INSTANCE
		}
		// loadCodeFiles 的参数是显式 java.util.List（为兼容 Java 实现类），Kotlin List 需桥接转换
		@Suppress("UNCHECKED_CAST")
		return checkNotNull(dexInput).loadCodeFiles(result.converted as java.util.List<Path>, result)
	}

	public companion object {
		public const val PLUGIN_ID = "java-convert"
	}
}
