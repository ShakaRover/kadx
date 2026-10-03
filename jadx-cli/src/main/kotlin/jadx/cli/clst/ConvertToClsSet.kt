package jadx.cli.clst

import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.args.UseSourceNameAsClassNameAlias
import jadx.core.clsp.ClsSet
import jadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EnumSet

/**
 * 把 dex / jar 转换为 jadx 的类集合文件（`.jcst`）的小工具。
 *
 * **做什么**：加载输入文件（只做类结构解析，关闭去混淆/重命名等耗时 Pass），
 * 构建 [ClsSet] 并保存为 `.jcst`。
 *
 * **为什么这样写**：带 `main` 的命令行工具，`main` 加 `@JvmStatic` 保留静态入口，
 * 其余方法放进 `companion object`。
 */
class ConvertToClsSet {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ConvertToClsSet::class.java)

		/** 打印用法说明。 */
		fun usage() {
			LOG.info("<android API level (number)> <output .jcst file> <several input dex or jar files> ")
			LOG.info(
				"Arguments to update core.jcst: " +
					"<android API level (number)> " +
					"<jadx root>/jadx-core/src/main/resources/clst/core.jcst " +
					"<sdk_root>/platforms/android-<api level>/android.jar" +
					"<sdk_root>/platforms/android-<api level>/optional/android.car.jar " +
					"<sdk_root>/platforms/android-<api level>/optional/org.apache.http.legacy.jar",
			)
		}

		/** 命令行入口。 */
		@JvmStatic
		fun main(args: Array<String>) {
			if (args.size != 5) {
				usage()
				System.exit(1)
			}
			val androidApiLevel = args[0].toInt()
			val inputPaths = args.drop(1).map { Paths.get(it) }.toMutableList()
			val output: Path = inputPaths.removeAt(0)

			val jadxArgs = JadxArgs()
			jadxArgs.inputFiles = FileUtils.toFiles(inputPaths).toMutableList()

			// 关闭 prepare 阶段不需要的 Pass
			jadxArgs.isDeobfuscationOn = false
			jadxArgs.renameFlags = EnumSet.noneOf(JadxArgs.RenameEnum::class.java)
			jadxArgs.useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.NEVER
			jadxArgs.isMoveInnerClasses = false
			jadxArgs.isInlineAnonymousClasses = false
			jadxArgs.isInlineMethods = false

			// 不要求也不加载已有的类集合文件
			jadxArgs.isLoadJadxClsSetFile = false

			try {
				JadxDecompiler(jadxArgs).use { decompiler ->
					decompiler.load()
					val root = checkNotNull(decompiler.getRoot())
					val set = ClsSet(root)
					set.setAndroidApiLevel(androidApiLevel)
					set.loadFrom(root)
					set.save(output)

					LOG.info("Output: {}", output)
					LOG.info("done")
				}
			} catch (e: Exception) {
				LOG.error("Failed with error", e)
			}
		}
	}
}
