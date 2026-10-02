package jadx.plugins.input.raung

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.data.JadxPluginRuntimeData
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.input.data.impl.EmptyCodeLoader
import java.nio.file.Path

/**
 * Raung 输入插件：注册 .raung 文件的加载能力。
 *
 * **背景**：init() 时向上下文注册一个代码输入——先把 .raung 文件经
 * [RaungConvert] 转成临时 jar，再委托给 java-input 插件的运行时数据解析。
 */
public class RaungInputPlugin : JadxPlugin {

	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfo("raung-input", "Raung Input", "Load .raung files")

	override fun init(context: JadxPluginContext) {
		val javaInput: JadxPluginRuntimeData = context.plugins().getProviding("java-input")
		// 注意：JadxCodeInput 是 Kotlin 接口，Kotlin lambda 不能做 SAM 转换（仅 Java 接口可以），需用 object 表达式
		context.addCodeInput(object : JadxCodeInput {
			override fun loadFiles(input: java.util.List<Path>): ICodeLoader {
				val convert = RaungConvert()
				if (!convert.execute(input)) {
					return EmptyCodeLoader.INSTANCE
				}
				// loadCodeFiles 的参数是显式 java.util.List（为兼容 Java 实现类），Kotlin List 需桥接转换
				@Suppress("UNCHECKED_CAST")
				return javaInput.loadCodeFiles(convert.getFiles() as java.util.List<Path>, convert)
			}
		})
	}
}
