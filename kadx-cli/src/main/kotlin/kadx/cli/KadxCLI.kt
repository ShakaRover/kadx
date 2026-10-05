package kadx.cli

import kadx.analysis.callgraph.KadxCallGraph
import kadx.api.KadxArgs
import kadx.api.KadxDecompiler
import kadx.api.impl.AnnotatedCodeWriter
import kadx.api.impl.NoOpCodeCache
import kadx.api.impl.SimpleCodeWriter
import kadx.api.usage.impl.EmptyUsageInfoCache
import kadx.cli.config.KadxConfigAdapter
import kadx.cli.plugins.KadxFilesGetter
import kadx.core.utils.exceptions.KadxArgsValidateException
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.plugins.tools.KadxExternalPluginsLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.function.Function

/**
 * kadx 命令行主入口。
 *
 * **做什么**：解析参数、构建 [KadxArgs]、加载输入并保存反编译结果；
 * 同时处理单类模式、调用图导出与错误退出码。
 *
 * **为什么这样写**：原 Java 全是静态方法且 `main` 是 JVM 入口点，因此放入 `companion object`
 * 并对 `main` 加 `@JvmStatic`，保证 `kadx.cli.KadxCLI.main` 静态入口不变。
 */
class KadxCLI {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(KadxCLI::class.java)

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

		fun execute(args: Array<String>): Int = execute(args, null)

		fun execute(args: Array<String>, argsMod: ((KadxArgs) -> Unit)?): Int {
			return try {
				val cliArgs = KadxCLIArgs.processArgs(
					args,
					KadxCLIArgs(),
					KadxConfigAdapter(KadxCLIArgs::class.java, "cli"),
				) ?: return 0
				val kadxArgs = buildArgs(cliArgs)
				argsMod?.invoke(kadxArgs)
				runSave(kadxArgs, cliArgs)
			} catch (e: KadxArgsValidateException) {
				LOG.error("Incorrect arguments: {}", e.message)
				1
			} catch (e: Throwable) {
				LOG.error("Process error:", e)
				1
			}
		}

		private fun buildArgs(cliArgs: KadxCLIArgs): KadxArgs {
			val kadxArgs = cliArgs.toKadxArgs()
			kadxArgs.codeCache = NoOpCodeCache()
			kadxArgs.usageInfoCache = EmptyUsageInfoCache()
			kadxArgs.pluginLoader = KadxExternalPluginsLoader()
			kadxArgs.filesGetter = KadxFilesGetter.INSTANCE
			initCodeWriterProvider(kadxArgs)
			KadxAppCommon.applyEnvVars(kadxArgs)
			return kadxArgs
		}

		private fun runSave(kadxArgs: KadxArgs, cliArgs: KadxCLIArgs): Int {
			KadxDecompiler(kadxArgs).use { kadx ->
				kadx.load()
				if (checkForErrors(kadx)) {
					return 2
				}
				writeCallGraph(kadx, cliArgs)
				if (!SingleClassMode.process(kadx, cliArgs)) {
					save(kadx)
				}
				val errorsCount = kadx.getErrorsCount()
				if (errorsCount != 0) {
					kadx.printErrorsReport()
					LOG.error("finished with errors, count: {}", errorsCount)
					return 3
				}
				LOG.info("done")
				return 0
			}
		}

		private fun initCodeWriterProvider(kadxArgs: KadxArgs) {
			when (kadxArgs.outputFormat) {
				KadxArgs.OutputFormatEnum.JAVA ->
					kadxArgs.codeWriterProvider = Function { args -> SimpleCodeWriter(args) }

				KadxArgs.OutputFormatEnum.JSON ->
					// 代码偏移与源码行号需要带注解的 writer
					kadxArgs.codeWriterProvider = Function { args -> AnnotatedCodeWriter(args) }
			}
		}

		private fun checkForErrors(kadx: KadxDecompiler): Boolean {
			val root = checkNotNull(kadx.getRoot())
			if (root.getClasses().isEmpty()) {
				if (kadx.getArgs().isSkipResources) {
					LOG.error("Load failed! No classes for decompile!")
					return true
				}
				if (!kadx.getArgs().isSkipSources) {
					LOG.warn("No classes to decompile; decoding resources only")
					kadx.getArgs().isSkipSources = true
				}
			}
			val errorsCount = kadx.getErrorsCount()
			if (errorsCount > 0) {
				LOG.error("Loading finished with errors! Count: {}", errorsCount)
				// 继续处理
			}
			return false
		}

		private fun save(kadx: KadxDecompiler) {
			if (LogHelper.logLevel == LogHelper.LogLevelEnum.QUIET) {
				kadx.save()
			} else {
				LOG.info("processing ...")
				kadx.save(500) { done, total ->
					val progress = (done * 100.0 / total).toInt()
					System.out.printf("INFO  - progress: %d of %d (%d%%)\r", done, total, progress)
				}
				// 简单清掉进度行
				System.out.print("                                                             \r")
			}
		}

		private fun writeCallGraph(kadx: KadxDecompiler, cliArgs: KadxCLIArgs) {
			val mode = cliArgs.callGraphSaveMode
			if (mode == KadxCLIArgs.CallGraphSaveMode.NONE) {
				return
			}
			val outPath = checkNotNull(kadx.getArgs().outDir).toPath()
			val callGraph = KadxCallGraph.builder(kadx)
				.resolvedOnly(true)
				.build()
			val cgPath: Path = when (mode) {
				KadxCLIArgs.CallGraphSaveMode.JSON -> outPath.resolve("callgraph.json").also { callGraph.writeJson(it) }
				KadxCLIArgs.CallGraphSaveMode.DOT -> outPath.resolve("callgraph.dot").also { callGraph.writeDot(it) }
				else -> throw KadxRuntimeException("Unexpected call graph save mode: $mode")
			}
			LOG.info("Call graph saved: {}", cgPath.toAbsolutePath())
		}
	}
}
