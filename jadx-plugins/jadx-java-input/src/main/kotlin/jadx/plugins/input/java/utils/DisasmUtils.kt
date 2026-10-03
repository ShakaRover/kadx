package jadx.plugins.input.java.utils

import io.github.skylot.raung.disasm.RaungDisasm
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

/**
 * 字节码反汇编工具。
 *
 * **做什么**：把 .class 文件的原始字节转成可读的反汇编文本，供 [JavaClassData] 在调试/日志场景使用。
 * 默认走内置的 Raung 反汇编器（纯 Java、无外部依赖）；保留了一个基于系统 `javap` 命令的备用实现用于对比调试。
 */
object DisasmUtils {

	private val LOG: Logger = LoggerFactory.getLogger(DisasmUtils::class.java)

	/** 用内置 Raung 反汇编器把 class 字节转成文本 */
	@JvmStatic
	fun get(bytes: ByteArray): String = useRaung(bytes)

	private fun useRaung(bytes: ByteArray): String = RaungDisasm.create().executeForBytes(bytes)

	/**
	 * 用系统 javap 作为临时反汇编器。
	 * Don't remove! Useful for debug.（原 Java 注释：调试对比用，勿删）
	 */
	private fun useSystemJavaP(bytes: ByteArray): String {
		var tmpCls: Path? = null
		try {
			tmpCls = Files.createTempFile("jadx", ".class")
			Files.write(tmpCls, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
			val process = Runtime.getRuntime().exec(
				arrayOf(
					"javap",
					"-constants",
					"-v",
					"-p",
					"-c",
					tmpCls.toAbsolutePath().toString(),
				),
			)
			process.waitFor(2, TimeUnit.SECONDS)
			return inputStreamToString(process.inputStream)
		} catch (e: Exception) {
			LOG.error("Java class disasm error", e)
			return "error"
		} finally {
			if (tmpCls != null) {
				Files.delete(tmpCls)
			}
		}
	}

	/** 把输入流完整读入并转成字符串（8KB 缓冲循环读取） */
	@JvmStatic
	@Throws(IOException::class)
	fun inputStreamToString(input: InputStream): String {
		val out = ByteArrayOutputStream()
		val buf = ByteArray(8 * 1024)
		while (true) {
			val r = input.read(buf)
			if (r == -1) {
				break
			}
			out.write(buf, 0, r)
		}
		return out.toString()
	}
}
