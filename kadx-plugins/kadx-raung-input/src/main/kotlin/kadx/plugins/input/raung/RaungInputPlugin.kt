package kadx.plugins.input.raung

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.data.KadxPluginRuntimeData
import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.input.data.impl.EmptyCodeLoader
import java.nio.file.Path

/**
 * Raung 输入插件：注册 .raung 文件的加载能力。
 *
 * **背景**：init() 时向上下文注册一个代码输入——先把 .raung 文件经
 * [RaungConvert] 转成临时 jar，再委托给 java-input 插件的运行时数据解析。
 */
public class RaungInputPlugin : KadxPlugin {

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfo("raung-input", "Raung Input", "Load .raung files")

	override fun init(context: KadxPluginContext) {
		val javaInput: KadxPluginRuntimeData = context.plugins().getProviding("java-input")
		// 注意：KadxCodeInput 是 Kotlin 接口，Kotlin lambda 不能做 SAM 转换（仅 Java 接口可以），需用 object 表达式
		context.addCodeInput(object : KadxCodeInput {
			override fun loadFiles(input: java.util.List<Path>): ICodeLoader {
				val convert = RaungConvert()
				if (!convert.execute(input)) {
					return EmptyCodeLoader.INSTANCE
				}
				// loadCodeFiles 的参数是显式 java.util.List（为兼容 Java 实现类），Kotlin List 需桥接转换
				@Suppress("UNCHECKED_CAST")
				return javaInput.loadCodeFiles(convert.files as java.util.List<Path>, convert)
			}
		})
	}
}
