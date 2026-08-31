package jadx.plugins.input.raung

import io.github.skylot.raung.asm.RaungAsm
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Raung 字节码转换工具：把 .raung 文件转成临时 jar。
 *
 * **背景**：Raung 是一种实验性 JVM 字节码格式，本类调用 raung-asm 库
 * 将其转换为标准 class 文件（打包为临时 jar），再交给 java-input 插件解析。
 * 实现 [java.io.Closeable]，关闭时删除临时文件。
 */
public class RaungConvert : java.io.Closeable {

	/** @Nullable 转换生成的临时 jar；execute() 成功前为 null */
	private var tmpJar: Path? = null

	/**
	 * 执行转换。
	 * @param input 输入文件路径列表（内部会过滤出 .raung 文件）
	 * @return 是否找到并成功转换了 raung 文件
	 */
	// 参数用 java.util.List：与 JadxCodeInput.loadFiles 的签名一致，避免调用方转换
	public fun execute(input: java.util.List<Path>): Boolean {
		val raungInputs = filterRaungFiles(input)
		if (raungInputs.isEmpty()) {
			return false
		}
		try {
			val jar = Files.createTempFile("jadx-raung-", ".jar")
			tmpJar = jar
			RaungAsm.create()
				.output(jar)
				.inputs(raungInputs)
				.execute()
			return true
		} catch (e: Exception) {
			LOG.error("Raung process error", e)
		}
		close()
		return false
	}

	private fun filterRaungFiles(input: java.util.List<Path>): List<Path> {
		val matcher = FileSystems.getDefault().getPathMatcher("glob:**.raung")
		return input.filter { matcher.matches(it) }
	}

	public fun getFiles(): List<Path> {
		val jar = tmpJar ?: return emptyList()
		return listOf(jar)
	}

	override fun close() {
		try {
			tmpJar?.let { Files.deleteIfExists(it) }
		} catch (e: Exception) {
			LOG.error("Failed to remove tmp jar file: {}", tmpJar, e)
		}
	}

	public companion object {
		private val LOG = LoggerFactory.getLogger(RaungConvert::class.java)
	}
}
