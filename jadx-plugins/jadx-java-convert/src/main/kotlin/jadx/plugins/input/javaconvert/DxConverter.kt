package jadx.plugins.input.javaconvert

import com.android.dx.command.dexer.DxContext
import com.android.dx.command.dexer.Main
import java.io.ByteArrayOutputStream
import java.nio.file.Path

/**
 * 用旧版 dx（com.android.dx）把 class/jar 转成 dex。
 *
 * **背景**：[DxArgs] 继承 dx 的 Main.Arguments，固定一组参数（multidex、优化、
 * localInfo、coreLibrary、debug、minSdk=28）。run() 捕获 dx 的错误输出，
 * 返回码非 0 时抛 RuntimeException 并附带错误信息。
 */
public class DxConverter {

	public companion object {
		private const val CHARSET_NAME = "UTF-8"

		@JvmStatic
		public fun run(path: Path, tempDirectory: Path) {
			var result: Int
			var dxErrors: String
			try {
				val out = ByteArrayOutputStream()
				val errOut = ByteArrayOutputStream()
				out.use { outStream ->
					errOut.use { errStream ->
						val context = DxContext(outStream, errStream)
						val args = DxArgs(
							context,
							tempDirectory.toAbsolutePath().toString(),
							arrayOf(path.toAbsolutePath().toString()),
						)
						result = Main(context).runDx(args)
						dxErrors = errStream.toString(CHARSET_NAME)
					}
				}
			} catch (e: Exception) {
				throw RuntimeException("dx exception: " + e.message, e)
			}
			if (result != 0) {
				throw RuntimeException("Java to dex conversion error, code: $result, errors: $dxErrors")
			}
		}
	}

	private class DxArgs(context: DxContext, dexDir: String, input: Array<String>) : Main.Arguments(context) {
		init {
			outName = dexDir
			fileNames = input
			jarOutput = false
			multiDex = true

			optimize = true
			localInfo = true
			coreLibrary = true

			debug = true
			warnings = true
			minSdkVersion = 28
		}
	}
}
