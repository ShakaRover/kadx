package jadx.plugins.input.dex.utils

import com.android.tools.smali.smali.Smali
import com.android.tools.smali.smali.SmaliOptions
import java.nio.file.Files
import java.nio.file.Path

/**
 * 测试用 smali 汇编工具：把 .smali 资源/文件汇编成临时 DEX 供 [jadx.plugins.input.dex.DexInputPluginTest] 加载。
 */
public class SmaliTestUtils {

	companion object {
		/**
		 * 从 classpath 资源路径汇编 smali，返回临时 DEX 文件。
		 * @throws AssertionError 汇编失败
		 */
		@JvmStatic
		public fun compileSmaliFromResource(res: String): Path = try {
			val input = java.nio.file.Paths.get(ClassLoader.getSystemResource(res).toURI())
			compileSmali(input)
		} catch (e: Exception) {
			throw AssertionError("Smali assemble error", e)
		}

		/**
		 * 汇编单个 smali 文件，返回临时 DEX 文件。
		 * @throws AssertionError 汇编失败
		 */
		@JvmStatic
		public fun compileSmali(input: Path): Path = try {
			val tempFile = Files.createTempFile("jadx", "smali.dex")
			compileSmali(tempFile, listOf(input))
			tempFile
		} catch (e: Exception) {
			throw AssertionError("Smali assemble error", e)
		}

		private fun compileSmali(output: Path, inputFiles: List<Path>) {
			try {
				val options = SmaliOptions()
				options.outputDexFile = output.toAbsolutePath().toString()
				val inputFileNames = inputFiles.map { it.toAbsolutePath().toString() }
				Smali.assemble(options, inputFileNames)
			} catch (e: Exception) {
				throw AssertionError("Smali assemble error", e)
			}
		}
	}
}
