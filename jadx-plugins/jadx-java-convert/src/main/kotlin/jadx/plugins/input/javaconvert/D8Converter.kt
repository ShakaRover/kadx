package jadx.plugins.input.javaconvert

import com.android.tools.r8.CompilationFailedException
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * 用 R8/D8 把 class/jar 转成 dex。
 *
 * **背景**：固定参数（minApi=30、DEBUG 模式、中间产物、禁用/启用 desugaring
 * 由选项决定）。[LogHandler] 把 D8 的诊断信息转发到 slf4j 日志。
 */
public class D8Converter {

	public companion object {
		private val LOG = LoggerFactory.getLogger(D8Converter::class.java)

		@JvmStatic
		public fun run(path: Path, tempDirectory: Path, options: JavaConvertOptions) {
			val d8Command = D8Command.builder(LogHandler())
				.addProgramFiles(path)
				.setOutput(tempDirectory, OutputMode.DexIndexed)
				.setMode(CompilationMode.DEBUG)
				.setMinApiLevel(30)
				.setIntermediate(true)
				.setDisableDesugaring(!options.isD8Desugar())
				.setEnableVerboseSyntheticNames(true)
				.setOptimizeMultidexForLinearAlloc(false)
				.setIncludeClassesChecksum(false)
				.build()
			D8.run(d8Command)
		}

		private fun format(diagnostic: Diagnostic): String = diagnostic.getDiagnosticMessage() + ", origin: " + diagnostic.getOrigin() + ", position: " + diagnostic.getPosition()
	}

	private class LogHandler : DiagnosticsHandler {
		override fun error(diagnostic: Diagnostic) {
			LOG.error("D8 error: {}", format(diagnostic))
		}

		override fun warning(diagnostic: Diagnostic) {
			LOG.warn("D8 warning: {}", format(diagnostic))
		}

		override fun info(diagnostic: Diagnostic) {
			LOG.info("D8 info: {}", format(diagnostic))
		}
	}
}
