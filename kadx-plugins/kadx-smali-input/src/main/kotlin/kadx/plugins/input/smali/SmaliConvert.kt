package kadx.plugins.input.smali

import com.android.tools.smali.smali.SmaliOptions
import kadx.plugins.input.dex.utils.IDexData
import kadx.plugins.input.dex.utils.SimpleDexData
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Smali 批量编译：把 .smali 文件汇编成 dex 数据。
 *
 * **背景**：[execute] 过滤出 .smali 文件后调用 [compile]；线程数 >1 且文件多于 1 个时
 * 用固定线程池并行编译（结果列表加同步包装），完成后按文件名排序保证输出稳定。
 */
public class SmaliConvert {

	private val dexDataList: MutableList<IDexData> = mutableListOf()

	// 参数用 java.util.List：与 KadxCodeInput.loadFiles 的签名一致，避免调用方转换
	public fun execute(input: java.util.List<Path>, options: SmaliInputOptions): Boolean {
		val smaliFiles = filterSmaliFiles(input)
		if (smaliFiles.isEmpty()) {
			return false
		}
		try {
			compile(smaliFiles, options)
		} catch (e: Exception) {
			LOG.error("Smali process error", e)
		}
		return dexDataList.isNotEmpty()
	}

	private fun compile(inputFiles: List<Path>, options: SmaliInputOptions) {
		val smaliOptions = SmaliOptions()
		smaliOptions.apiLevel = options.apiLevel
		smaliOptions.verboseErrors = true
		smaliOptions.allowOdexOpcodes = false
		smaliOptions.printTokens = false

		val threads = options.threads
		LOG.debug("Compiling smali files: {}, threads: {}", inputFiles.size, threads)
		val start = System.currentTimeMillis()
		if (threads == 1 || inputFiles.size == 1) {
			for (inputFile in inputFiles) {
				assemble(dexDataList, inputFile, smaliOptions)
			}
		} else {
			try {
				val executor = Executors.newFixedThreadPool(threads)
				val syncList: MutableList<IDexData> = java.util.Collections.synchronizedList(dexDataList)
				for (inputFile in inputFiles) {
					executor.execute { assemble(syncList, inputFile, smaliOptions) }
				}
				executor.shutdown()
				executor.awaitTermination(1, TimeUnit.HOURS)
				dexDataList.sortBy { it.fileName }
			} catch (e: InterruptedException) {
				LOG.error("Smali compile interrupted", e)
			}
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("Smali compile done in: {}ms", System.currentTimeMillis() - start)
		}
	}

	private fun assemble(results: MutableList<IDexData>, inputFile: Path, smaliOptions: SmaliOptions) {
		val path = inputFile.toAbsolutePath()
		try {
			val dexContent = SmaliUtils.assemble(path.toFile(), smaliOptions)
			results.add(SimpleDexData(path.toString(), dexContent))
		} catch (e: Exception) {
			LOG.error("Failed to assemble smali file: {}", path, e)
		}
	}

	private fun filterSmaliFiles(input: java.util.List<Path>): List<Path> {
		val matcher = FileSystems.getDefault().getPathMatcher("glob:**.smali")
		return input.filter { matcher.matches(it) }
	}

	public val dexData: List<IDexData> get() = dexDataList

	public companion object {
		private val LOG = LoggerFactory.getLogger(SmaliConvert::class.java)
	}
}
