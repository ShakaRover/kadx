package kadx.plugins.input.dex

import kadx.api.plugins.KadxPlugin
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.KadxPluginInfo
import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.input.data.impl.EmptyCodeLoader
import kadx.api.plugins.utils.CommonFileUtils
import kadx.plugins.input.dex.utils.IDexData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
public class DexInputPlugin : KadxPlugin {

	private val options: DexInputOptions = DexInputOptions()
	private val loader: DexFileLoader = DexFileLoader(options)

	override fun getPluginInfo(): KadxPluginInfo = KadxPluginInfo(PLUGIN_ID, "Dex Input", "Load .dex and .apk files")

	override fun init(context: KadxPluginContext) {
		context.registerOptions(options)
		// 注意：KadxCodeInput 是 Kotlin 接口，Kotlin 侧不能对 Kotlin 接口做 SAM 转换，必须用 object 表达式
		context.addCodeInput(object : KadxCodeInput {
			override fun loadFiles(input: java.util.List<Path>): ICodeLoader {
				// 接口声明的是 java.util.List，Kotlin 侧 List 与它互不兼容，需经 java.util.ArrayList 中转
				val list = java.util.ArrayList<Path>()
				list.addAll(input)
				return this@DexInputPlugin.loadFiles(list)
			}
		})
		loader.setZipReader(context.getZipReader())
		// S3-A：mmap 路径需要临时目录把解压后的 dex 落盘；取不到就自动退回堆内路径
		try {
			loader.setTempDir(context.getArgs().filesGetter.getTempDir())
		} catch (e: Exception) {
			LOG.warn("Can't resolve temp dir for dex mmap, falling back to heap buffers", e)
		}
		// S3-B：跨会话 dex 解压持久缓存（系统缓存目录，不用临时目录——要跨会话存活）
		try {
			loader.setCacheDir(context.getArgs().filesGetter.getCacheDir())
		} catch (e: Exception) {
			LOG.warn("Can't resolve cache dir for dex disk cache, cache disabled", e)
		}
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
			readers.addAll(loader.loadDexReaders(data.fileName, data.content))
		}
		return DexLoadResult(readers, null)
	}

	public companion object {
		public const val PLUGIN_ID = "dex-input"

		private val LOG: Logger = LoggerFactory.getLogger(DexInputPlugin::class.java)
	}
}
