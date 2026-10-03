package jadx.cli

import jadx.analysis.callgraph.JadxCallGraph
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import jadx.api.impl.AnnotatedCodeWriter
import jadx.api.impl.NoOpCodeCache
import jadx.api.impl.SimpleCodeWriter
import jadx.api.usage.impl.EmptyUsageInfoCache
import jadx.cli.config.JadxConfigAdapter
import jadx.cli.plugins.JadxFilesGetter
import jadx.core.utils.exceptions.JadxArgsValidateException
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.plugins.tools.JadxExternalPluginsLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.function.Function

/**
 * jadx 命令行主入口。
 *
 * **做什么**：解析参数、构建 [JadxArgs]、加载输入并保存反编译结果；
 * 同时处理单类模式、调用图导出与错误退出码。
 *
 * **为什么这样写**：原 Java 全是静态方法且 `main` 是 JVM 入口点，因此放入 `companion object`
 * 并对 `main`/`execute` 加 `@JvmStatic`，保证 `jadx.cli.JadxCLI.main` 静态入口不变。
 */
class JadxCLI {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxCLI::class.java)

		/** JVM 入口点。 */
		@JvmStatic
		fun main(args: Array<String>) {
			var result = 1
			try {
				result = execute(args)
			} finally {
				System.exit(result)
			}
		}

		@JvmStatic
		fun execute(args: Array<String>): Int = execute(args, null)

		@JvmStatic
		fun execute(args: Array<String>, argsMod: ((JadxArgs) -> Unit)?): Int {
			return try {
				val cliArgs = JadxCLIArgs.processArgs(
					args,
					JadxCLIArgs(),
					JadxConfigAdapter(JadxCLIArgs::class.java, "cli"),
				) ?: return 0
				val jadxArgs = buildArgs(cliArgs)
				argsMod?.invoke(jadxArgs)
				runSave(jadxArgs, cliArgs)
			} catch (e: JadxArgsValidateException) {
				LOG.error("Incorrect arguments: {}", e.message)
				1
			} catch (e: Throwable) {
				LOG.error("Process error:", e)
				1
			}
		}

		private fun buildArgs(cliArgs: JadxCLIArgs): JadxArgs {
			val jadxArgs = cliArgs.toJadxArgs()
			jadxArgs.codeCache = NoOpCodeCache()
			jadxArgs.usageInfoCache = EmptyUsageInfoCache()
			jadxArgs.pluginLoader = JadxExternalPluginsLoader()
			jadxArgs.filesGetter = JadxFilesGetter.INSTANCE
			initCodeWriterProvider(jadxArgs)
			JadxAppCommon.applyEnvVars(jadxArgs)
			return jadxArgs
		}

		private fun runSave(jadxArgs: JadxArgs, cliArgs: JadxCLIArgs): Int {
			JadxDecompiler(jadxArgs).use { jadx ->
				jadx.load()
				if (checkForErrors(jadx)) {
					return 2
				}
				writeCallGraph(jadx, cliArgs)
				if (!SingleClassMode.process(jadx, cliArgs)) {
					save(jadx)
				}
				val errorsCount = jadx.getErrorsCount()
				if (errorsCount != 0) {
					jadx.printErrorsReport()
					LOG.error("finished with errors, count: {}", errorsCount)
					return 3
				}
				LOG.info("done")
				return 0
			}
		}

		private fun initCodeWriterProvider(jadxArgs: JadxArgs) {
			when (jadxArgs.outputFormat) {
				JadxArgs.OutputFormatEnum.JAVA ->
					jadxArgs.codeWriterProvider = Function { args -> SimpleCodeWriter(args) }

				JadxArgs.OutputFormatEnum.JSON ->
					// 代码偏移与源码行号需要带注解的 writer
					jadxArgs.codeWriterProvider = Function { args -> AnnotatedCodeWriter(args) }
			}
		}

		private fun checkForErrors(jadx: JadxDecompiler): Boolean {
			val root = checkNotNull(jadx.getRoot())
			if (root.getClasses().isEmpty()) {
				if (jadx.getArgs().isSkipResources) {
					LOG.error("Load failed! No classes for decompile!")
					return true
				}
				if (!jadx.getArgs().isSkipSources) {
					LOG.warn("No classes to decompile; decoding resources only")
					jadx.getArgs().isSkipSources = true
				}
			}
			val errorsCount = jadx.getErrorsCount()
			if (errorsCount > 0) {
				LOG.error("Loading finished with errors! Count: {}", errorsCount)
				// 继续处理
			}
			return false
		}

		private fun save(jadx: JadxDecompiler) {
			if (LogHelper.getLogLevel() == LogHelper.LogLevelEnum.QUIET) {
				jadx.save()
			} else {
				LOG.info("processing ...")
				jadx.save(500) { done, total ->
					val progress = (done * 100.0 / total).toInt()
					System.out.printf("INFO  - progress: %d of %d (%d%%)\r", done, total, progress)
				}
				// 简单清掉进度行
				System.out.print("                                                             \r")
			}
		}

		private fun writeCallGraph(jadx: JadxDecompiler, cliArgs: JadxCLIArgs) {
			val mode = cliArgs.callGraphSaveMode
			if (mode == JadxCLIArgs.CallGraphSaveMode.NONE) {
				return
			}
			val outPath = checkNotNull(jadx.getArgs().outDir).toPath()
			val callGraph = JadxCallGraph.builder(jadx)
				.resolvedOnly(true)
				.build()
			val cgPath: Path = when (mode) {
				JadxCLIArgs.CallGraphSaveMode.JSON -> outPath.resolve("callgraph.json").also { callGraph.writeJson(it) }
				JadxCLIArgs.CallGraphSaveMode.DOT -> outPath.resolve("callgraph.dot").also { callGraph.writeDot(it) }
				else -> throw JadxRuntimeException("Unexpected call graph save mode: $mode")
			}
			LOG.info("Call graph saved: {}", cgPath.toAbsolutePath())
		}
	}
}
