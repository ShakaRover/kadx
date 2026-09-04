package jadx.plugins.input.dex

import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.input.data.impl.EmptyCodeLoader
import jadx.api.plugins.utils.CommonFileUtils
import jadx.plugins.input.dex.utils.IDexData
import java.io.Closeable
import java.io.InputStream
import java.nio.file.Path

/**
 * DEX 输入插件入口（经 SPI META-INF/services 注册）。
 *
 * **背景**：
 * 1. [init] 注册选项、代码输入与全局 zip reader；
 * 2. [loadFiles] 是文件路径（apk/zip/dex）输入的入口；
 * 3. [loadDex] / [loadDexFromInputStream] / [loadDexData] 是内存字节数组入口
 *    （由 smali-input、java-convert 等插件在汇编/转换出 dex 后调用）；
 * 4. 所有入口统一交付 [ICodeLoader]（为空时返回 EmptyCodeLoader 单例）。
 */
public class DexInputPlugin : JadxPlugin {

	private val options: DexInputOptions = DexInputOptions()
	private val loader: DexFileLoader = DexFileLoader(options)

	override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfo(PLUGIN_ID, "Dex Input", "Load .dex and .apk files")

	override fun init(context: JadxPluginContext) {
		context.registerOptions(options)
		// 注意：JadxCodeInput 是 Kotlin 接口，Kotlin 侧不能对 Kotlin 接口做 SAM 转换，必须用 object 表达式
		context.addCodeInput(object : JadxCodeInput {
			override fun loadFiles(input: java.util.List<Path>): ICodeLoader {
				// 接口声明的是 java.util.List，Kotlin 侧 List 与它互不兼容，需经 java.util.ArrayList 中转
				val list = java.util.ArrayList<Path>()
				list.addAll(input)
				return this@DexInputPlugin.loadFiles(list)
			}
		})
		loader.setZipReader(context.getZipReader())
	}

	/**
	 * 按文件路径加载（委托 [DexFileLoader.collectDexFiles]）。
	 */
	public fun loadFiles(input: kotlin.collections.List<Path>): ICodeLoader = loadFiles(input, null)

	public fun loadFiles(inputFiles: kotlin.collections.List<Path>, closeable: Closeable?): ICodeLoader {
		val dexReaders = loader.collectDexFiles(inputFiles)
		if (dexReaders.isEmpty()) {
			return EmptyCodeLoader.INSTANCE
		}
		return DexLoadResult(dexReaders, closeable)
	}

	public fun loadDex(content: ByteArray, fileName: String?): ICodeLoader {
		val fileLabel = fileName ?: "input.dex"
		val dexReaders = loader.loadDexReaders(fileLabel, content)
		return DexLoadResult(dexReaders, null)
	}

	public fun loadDexFromInputStream(inStream: InputStream, fileLabel: String?): ICodeLoader = try {
		loadDex(CommonFileUtils.loadBytes(inStream), fileLabel)
	} catch (e: Exception) {
		throw DexException("Failed to read input stream", e)
	}

	public fun loadDexData(list: kotlin.collections.List<IDexData>): ICodeLoader {
		val readers = ArrayList<DexReader>()
		for (data in list) {
			readers.addAll(loader.loadDexReaders(data.getFileName(), data.getContent()))
		}
		return DexLoadResult(readers, null)
	}

	public companion object {
		public const val PLUGIN_ID = "dex-input"
	}
}
