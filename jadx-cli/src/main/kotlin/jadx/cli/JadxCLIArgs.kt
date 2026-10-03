package jadx.cli

import com.beust.jcommander.DynamicParameter
import com.beust.jcommander.IStringConverter
import com.beust.jcommander.Parameter
import jadx.api.CommentsLevel
import jadx.api.DecompilationMode
import jadx.api.JadxArgs
import jadx.api.JadxArgs.RenameEnum
import jadx.api.JadxArgs.UseKotlinMethodsForVarNames
import jadx.api.JadxDecompiler
import jadx.api.args.GeneratedRenamesMappingFileMode
import jadx.api.args.IntegerFormat
import jadx.api.args.ResourceNameSource
import jadx.api.args.UseSourceNameAsClassNameAlias
import jadx.api.args.UserRenamesMappingsMode
import jadx.cli.config.IJadxConfig
import jadx.cli.config.JadxConfigAdapter
import jadx.cli.config.JadxConfigExclude
import jadx.commons.app.JadxCommonFiles
import jadx.commons.app.JadxTempFiles
import jadx.core.deobf.conditions.DeobfWhitelist
import jadx.core.export.ExportGradleType
import jadx.core.utils.exceptions.JadxArgsValidateException
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.Collections
import java.util.EnumSet
import java.util.HashMap
import java.util.Locale
import java.util.function.Function
import java.util.function.Supplier

/**
 * jadx CLI 的全部参数对象。
 *
 * **做什么**：既作为 jcommander 的解析目标（`@Parameter` 注解），又作为 JSON 配置对象
 * （实现 [IJadxConfig]），最终转换成 jadx-core 的 [JadxArgs]。
 *
 * **为什么字段用 Kotlin 属性**：jcommander 通过**反射字段**读写参数，Gson 也通过字段读写配置。
 * Kotlin 属性的背后字段名与原 Java 字段名完全一致，注解（`@Parameter`/`@JadxConfigExclude`）
 * 默认落在该字段上，因此解析与配置文件格式都保持不变。布尔字段的原 getter 是 `isXxx()`，
 * 用 `@get:JvmName("isXxx")` 保留 JVM 方法名。
 *
 * 注意：`logLevel` 与 `useSourceNameAsClassNameAlias` 需要 `@JvmField`，
 * 前者是 jadx-gui 的 Java 子类会直接读写字段，后者是 getter 带自定义逻辑（不能与属性访问器共存）。
 */
open class JadxCLIArgs : IJadxConfig {

	@JadxConfigExclude
	@Parameter(description = "<input files> (.apk, .dex, .jar, .class, .smali, .zip, .aar, .arsc, .aab, .xapk, .apkm, .jadx.kts)")
	var files: List<String> = Collections.emptyList()

	@JadxConfigExclude
	@Parameter(names = ["-d", "--output-dir"], description = "output directory")
	var outDir: String? = null

	@JadxConfigExclude
	@Parameter(names = ["-ds", "--output-dir-src"], description = "output directory for sources")
	var outDirSrc: String? = null

	@JadxConfigExclude
	@Parameter(names = ["-dr", "--output-dir-res"], description = "output directory for resources")
	var outDirRes: String? = null

	@Parameter(names = ["-r", "--no-res"], description = "do not decode resources")
	@get:JvmName("isSkipResources")
	var skipResources: Boolean = false

	@Parameter(names = ["-s", "--no-src"], description = "do not decompile source code")
	@get:JvmName("isSkipSources")
	var skipSources: Boolean = false

	@Parameter(names = ["-j", "--threads-count"], description = "processing threads count")
	var threadsCount: Int = JadxArgs.DEFAULT_THREADS_COUNT

	@JadxConfigExclude
	@Parameter(names = ["--single-class"], description = "decompile a single class, full name, raw or alias")
	var singleClass: String? = null

	@JadxConfigExclude
	@Parameter(names = ["--single-class-output"], description = "file or dir for write if decompile a single class")
	var singleClassOutput: String? = null

	@Parameter(names = ["--output-format"], description = "can be 'java' or 'json'")
	var outputFormat: String = "java"

	@Parameter(names = ["-e", "--export-gradle"], description = "save as gradle project (set '--export-gradle-type' to 'auto')")
	@get:JvmName("isExportAsGradleProject")
	var exportAsGradleProject: Boolean = false

	@Parameter(
		names = ["--export-gradle-type"],
		description = "Gradle project template for export:" +
			"\n 'auto' - detect automatically" +
			"\n 'android-app' - Android Application (apk)" +
			"\n 'android-library' - Android Library (aar)" +
			"\n 'simple-java' - simple Java",
		converter = ExportGradleTypeConverter::class,
	)
	var exportGradleType: ExportGradleType? = null

	@Parameter(
		names = ["-m", "--decompilation-mode"],
		description = "code output mode:" +
			"\n 'auto' - trying best options (default)" +
			"\n 'restructure' - restore code structure (normal java code)" +
			"\n 'simple' - simplified instructions (linear, with goto's)" +
			"\n 'fallback' - raw instructions without modifications",
		converter = DecompilationModeConverter::class,
	)
	var decompilationMode: DecompilationMode = DecompilationMode.AUTO

	@Parameter(names = ["--show-bad-code"], description = "show inconsistent code (incorrectly decompiled)")
	@get:JvmName("isShowInconsistentCode")
	var showInconsistentCode: Boolean = false

	@Parameter(names = ["--no-xml-pretty-print"], description = "do not prettify XML")
	@get:JvmName("isSkipXmlPrettyPrint")
	var skipXmlPrettyPrint: Boolean = false

	@Parameter(names = ["--no-imports"], description = "disable use of imports, always write entire package name")
	@get:JvmName("isUseImports")
	var useImports: Boolean = true

	@Parameter(names = ["--no-debug-info"], description = "disable debug info parsing and processing")
	@get:JvmName("isDebugInfo")
	var debugInfo: Boolean = true

	@Parameter(names = ["--add-debug-lines"], description = "add comments with debug line numbers if available")
	@get:JvmName("isAddDebugLines")
	var addDebugLines: Boolean = false

	@Parameter(names = ["--no-inline-anonymous"], description = "disable anonymous classes inline")
	@get:JvmName("isInlineAnonymousClasses")
	var inlineAnonymousClasses: Boolean = true

	@Parameter(names = ["--no-inline-methods"], description = "disable methods inline")
	@get:JvmName("isInlineMethods")
	var inlineMethods: Boolean = true

	@Parameter(names = ["--no-move-inner-classes"], description = "disable move inner classes into parent")
	@get:JvmName("isMoveInnerClasses")
	var moveInnerClasses: Boolean = true

	@Parameter(names = ["--no-inline-kotlin-lambda"], description = "disable inline for Kotlin lambdas")
	@get:JvmName("isAllowInlineKotlinLambda")
	var allowInlineKotlinLambda: Boolean = true

	@Parameter(names = ["--no-finally"], description = "don't extract finally block")
	@get:JvmName("isExtractFinally")
	var extractFinally: Boolean = true

	@Parameter(names = ["--no-restore-switch-over-string"], description = "don't restore switch over string")
	@get:JvmName("isRestoreSwitchOverString")
	var restoreSwitchOverString: Boolean = true

	@Parameter(names = ["--no-replace-consts"], description = "don't replace constant value with matching constant field")
	@get:JvmName("isReplaceConsts")
	var replaceConsts: Boolean = true

	@Parameter(names = ["--escape-unicode"], description = "escape non latin characters in strings (with \\u)")
	@get:JvmName("isEscapeUnicode")
	var escapeUnicode: Boolean = false

	@Parameter(names = ["--respect-bytecode-access-modifiers"], description = "don't change original access modifiers")
	@get:JvmName("isRespectBytecodeAccessModifiers")
	var respectBytecodeAccessModifiers: Boolean = false

	@Parameter(
		names = ["--mappings-path"],
		description = "deobfuscation mappings file or directory. Allowed formats: Tiny and Tiny v2 (both '.tiny'), Enigma (.mapping) or Enigma directory",
	)
	var userRenamesMappingsPath: Path? = null

	@Parameter(
		names = ["--mappings-mode"],
		description = "set mode for handling the deobfuscation mapping file:" +
			"\n 'read' - just read, user can always save manually (default)" +
			"\n 'read-and-autosave-every-change' - read and autosave after every change" +
			"\n 'read-and-autosave-before-closing' - read and autosave before exiting the app or closing the project" +
			"\n 'ignore' - don't read or save (can be used to skip loading mapping files referenced in the project file)",
	)
	var userRenamesMappingsMode: UserRenamesMappingsMode = UserRenamesMappingsMode.getDefault()

	@Parameter(names = ["--deobf"], description = "activate deobfuscation")
	@get:JvmName("isDeobfuscationOn")
	var deobfuscationOn: Boolean = false

	@Parameter(names = ["--deobf-min"], description = "min length of name, renamed if shorter")
	var deobfuscationMinLength: Int = 3

	@Parameter(names = ["--deobf-max"], description = "max length of name, renamed if longer")
	var deobfuscationMaxLength: Int = 64

	@Parameter(
		names = ["--deobf-whitelist"],
		description = "space separated list of classes (full name) and packages (ends with '.*') to exclude from deobfuscation",
	)
	var deobfuscationWhitelistStr: String = DeobfWhitelist.DEFAULT_STR

	@JadxConfigExclude
	@Parameter(
		names = ["--deobf-cfg-file"],
		description = "deobfuscation mappings file used for JADX auto-generated names (in the JOBF file format)," +
			" default: same dir and name as input file with '.jobf' extension",
	)
	var generatedRenamesMappingFile: String? = null

	@Parameter(
		names = ["--deobf-cfg-file-mode"],
		description = "set mode for handling the JADX auto-generated names' deobfuscation map file:" +
			"\n 'read' - read if found, don't save (default)" +
			"\n 'read-or-save' - read if found, save otherwise (don't overwrite)" +
			"\n 'overwrite' - don't read, always save" +
			"\n 'ignore' - don't read and don't save",
		converter = DeobfuscationMapFileModeConverter::class,
	)
	var generatedRenamesMappingFileMode: GeneratedRenamesMappingFileMode = GeneratedRenamesMappingFileMode.getDefault()

	@Suppress("DeprecatedIsStillUsed")
	@Parameter(
		names = ["--deobf-use-sourcename"],
		description = "use source file name as class name alias." +
			"\nDEPRECATED, use \"--use-source-name-as-class-name-alias\" instead",
		hidden = true,
	)
	@Deprecated("Use \"--use-source-name-as-class-name-alias\" instead")
	var deobfuscationUseSourceNameAsAlias: Boolean? = null

	@Parameter(
		names = ["--deobf-res-name-source"],
		description = "better name source for resources:" +
			"\n 'auto' - automatically select best name (default)" +
			"\n 'resources' - use resources names" +
			"\n 'code' - use R class fields names",
		converter = ResourceNameSourceConverter::class,
	)
	var resourceNameSource: ResourceNameSource = ResourceNameSource.AUTO

	@JvmField
	@Parameter(
		names = ["--use-source-name-as-class-name-alias"],
		description = "use source name as class name alias:" +
			"\n 'always' - always use source name if it's available" +
			"\n 'if-better' - use source name if it seems better than the current one" +
			"\n 'never' - never use source name, even if it's available",
		converter = UseSourceNameAsClassNameConverter::class,
	)
	protected var useSourceNameAsClassNameAlias: UseSourceNameAsClassNameAlias? = null

	@Parameter(
		names = ["--source-name-repeat-limit"],
		description = "allow using source name if it appears less than a limit number",
	)
	var sourceNameRepeatLimit: Int = 10

	@Parameter(
		names = ["--use-kotlin-methods-for-var-names"],
		description = "use kotlin intrinsic methods to rename variables, values: disable, apply, apply-and-hide",
		converter = UseKotlinMethodsForVarNamesConverter::class,
	)
	var useKotlinMethodsForVarNames: UseKotlinMethodsForVarNames = UseKotlinMethodsForVarNames.APPLY

	@Parameter(
		names = ["--use-headers-for-detect-resource-extensions"],
		description = "Use headers for detect resource extensions if resource obfuscated",
	)
	@get:JvmName("isUseHeadersForDetectResourceExtensions")
	var useHeadersForDetectResourceExtensions: Boolean = false

	@Parameter(
		names = ["--rename-flags"],
		description = "fix options (comma-separated list of):" +
			"\n 'case' - fix case sensitivity issues (according to --fs-case-sensitive option)," +
			"\n 'valid' - rename java identifiers to make them valid," +
			"\n 'printable' - remove non-printable chars from identifiers," +
			"\nor single 'none' - to disable all renames" +
			"\nor single 'all' - to enable all (default)",
		listConverter = RenameConverter::class,
	)
	var renameFlags: MutableSet<RenameEnum> = EnumSet.allOf(RenameEnum::class.java)

	@Parameter(
		names = ["--integer-format"],
		description = "how integers are displayed:" +
			"\n 'auto' - automatically select (default)" +
			"\n 'decimal' - use decimal" +
			"\n 'hexadecimal' - use hexadecimal",
		converter = IntegerFormatConverter::class,
	)
	var integerFormat: IntegerFormat = IntegerFormat.AUTO

	@Parameter(names = ["--type-update-limit"], description = "type update limit count (per one instruction)")
	var typeUpdatesLimitCount: Int = 10

	@Parameter(names = ["--fs-case-sensitive"], description = "treat filesystem as case sensitive, false by default")
	@get:JvmName("isFsCaseSensitive")
	var fsCaseSensitive: Boolean = false

	@Parameter(names = ["--cfg"], description = "save methods control flow graph to dot file")
	@get:JvmName("isCfgOutput")
	var cfgOutput: Boolean = false

	@Parameter(names = ["--raw-cfg"], description = "save methods control flow graph (use raw instructions)")
	@get:JvmName("isRawCfgOutput")
	var rawCfgOutput: Boolean = false

	@Parameter(
		names = ["--call-graph"],
		description = "save app call graph in format: 'dot' or 'json'",
		converter = CallGraphSaveModeConverter::class,
	)
	var callGraphSaveMode: CallGraphSaveMode = CallGraphSaveMode.NONE

	@Parameter(names = ["-f", "--fallback"], description = "set '--decompilation-mode' to 'fallback' (deprecated)")
	@get:JvmName("isFallbackMode")
	var fallbackMode: Boolean = false

	@Parameter(names = ["--use-dx"], description = "use dx/d8 to convert java bytecode")
	@get:JvmName("isUseDx")
	var useDx: Boolean = false

	@Parameter(
		names = ["--comments-level"],
		description = "set code comments level, values: error, warn, info, debug, user-only, none",
		converter = CommentsLevelConverter::class,
	)
	var commentsLevel: CommentsLevel = CommentsLevel.INFO

	@JvmField
	@Parameter(
		names = ["--log-level"],
		description = "set log level, values: quiet, progress, error, warn, info, debug",
		converter = LogLevelConverter::class,
	)
	protected var logLevel: LogHelper.LogLevelEnum = LogHelper.LogLevelEnum.PROGRESS

	@JadxConfigExclude
	@Parameter(names = ["-v", "--verbose"], description = "verbose output (set --log-level to DEBUG)")
	var verbose: Boolean = false

	@JadxConfigExclude
	@Parameter(names = ["-q", "--quiet"], description = "turn off output (set --log-level to QUIET)")
	var quiet: Boolean = false

	@JadxConfigExclude
	@Parameter(names = ["--disable-plugins"], description = "comma separated list of plugin ids to disable")
	var disablePlugins: String = ""

	@JadxConfigExclude
	@Parameter(
		names = ["--config"],
		defaultValueDescription = "<config-ref>",
		description = "load configuration from file, <config-ref> can be:" +
			"\n path to '.json' file" +
			"\n short name - uses file with this name from config directory" +
			"\n 'none' - to disable config loading",
	)
	var config: String = ""

	@JadxConfigExclude
	@Parameter(
		names = ["--save-config"],
		defaultValueDescription = "<config-ref>",
		description = "save current options into configuration file and exit, <config-ref> can be:" +
			"\n empty - for default config" +
			"\n path to '.json' file" +
			"\n short name - file will be saved in config directory",
	)
	var saveConfig: String? = null

	@JadxConfigExclude
	@Parameter(names = ["--print-files"], description = "print files and directories used by jadx (config, cache, temp)")
	var printFiles: Boolean = false

	@JadxConfigExclude
	@Parameter(names = ["--version"], description = "print jadx version")
	var printVersion: Boolean = false

	@JadxConfigExclude
	@Parameter(names = ["-h", "--help"], description = "print this help", help = true)
	var printHelp: Boolean = false

	@DynamicParameter(names = ["-P"], description = "Plugin options", hidden = true)
	var pluginOptions: Map<String, String> = HashMap()

	/**
	 * 已废弃的入口：不加载配置文件，仅解析命令行。
	 * 推荐使用 [processArgs] 重载。
	 */
	fun processArgs(args: Array<String>): Boolean = JadxCLIArgs.processArgs(args, this, null) != null

	/** 处理子命令与提前退出标志。返回 false 表示无需继续执行。 */
	fun process(jcw: JCommanderWrapper): Boolean {
		if (jcw.processCommands()) {
			return false
		}
		if (printHelp) {
			jcw.printUsage()
			return false
		}
		if (printVersion) {
			println(JadxDecompiler.getVersion())
			return false
		}
		// 未知选项会被加入 files，这里做检查
		for (fileName in files) {
			if (fileName.startsWith("-")) {
				throw JadxArgsValidateException("Unknown option: $fileName")
			}
		}
		return true
	}

	/** 校验参数合法性。 */
	fun verify() {
		if (threadsCount <= 0) {
			throw JadxArgsValidateException("Threads count must be positive, got: $threadsCount")
		}
	}

	/** 把 CLI 参数转换为 jadx-core 使用的 [JadxArgs]。 */
	fun toJadxArgs(): JadxArgs {
		val args = JadxArgs()
		args.inputFiles = files.mapNotNull { FileUtils.toFile(it) }.toMutableList()
		args.outDir = FileUtils.toFile(outDir)
		args.outDirSrc = FileUtils.toFile(outDirSrc)
		args.outDirRes = FileUtils.toFile(outDirRes)
		args.outputFormat = JadxArgs.OutputFormatEnum.valueOf(outputFormat.uppercase(Locale.getDefault()))
		args.threadsCount = threadsCount
		args.isSkipSources = skipSources
		args.isSkipResources = skipResources
		if (fallbackMode) {
			args.decompilationMode = DecompilationMode.FALLBACK
		} else {
			args.decompilationMode = decompilationMode
		}
		args.isShowInconsistentCode = showInconsistentCode
		args.isCfgOutput = cfgOutput
		args.isRawCFGOutput = rawCfgOutput
		args.isReplaceConsts = replaceConsts
		if (userRenamesMappingsPath != null) {
			args.userRenamesMappingsPath = userRenamesMappingsPath
		}
		args.userRenamesMappingsMode = userRenamesMappingsMode
		args.isDeobfuscationOn = deobfuscationOn
		args.generatedRenamesMappingFile = FileUtils.toFile(generatedRenamesMappingFile)
		args.generatedRenamesMappingFileMode = generatedRenamesMappingFileMode
		args.deobfuscationMinLength = deobfuscationMinLength
		args.deobfuscationMaxLength = deobfuscationMaxLength
		args.deobfuscationWhitelist = deobfuscationWhitelistStr.split(" ")
		args.useSourceNameAsClassNameAlias = getUseSourceNameAsClassNameAlias()
		args.isUseHeadersForDetectResourceExtensions = useHeadersForDetectResourceExtensions
		args.sourceNameRepeatLimit = sourceNameRepeatLimit
		args.useKotlinMethodsForVarNames = useKotlinMethodsForVarNames
		args.resourceNameSource = resourceNameSource
		args.isEscapeUnicode = escapeUnicode
		args.isRespectBytecodeAccModifiers = respectBytecodeAccessModifiers
		args.exportGradleType = exportGradleType
		if (exportAsGradleProject && exportGradleType == null) {
			args.exportGradleType = ExportGradleType.AUTO
		}
		args.isSkipXmlPrettyPrint = skipXmlPrettyPrint
		args.isUseImports = useImports
		args.isDebugInfo = debugInfo
		args.isInsertDebugLines = addDebugLines
		args.isInlineAnonymousClasses = inlineAnonymousClasses
		args.isInlineMethods = inlineMethods
		args.isMoveInnerClasses = moveInnerClasses
		args.isAllowInlineKotlinLambda = allowInlineKotlinLambda
		args.isExtractFinally = extractFinally
		args.isRestoreSwitchOverString = restoreSwitchOverString
		args.renameFlags = buildEnumSetForRenameFlags()
		args.isFsCaseSensitive = fsCaseSensitive
		args.commentsLevel = commentsLevel
		args.integerFormat = integerFormat
		args.typeUpdatesLimitCount = typeUpdatesLimitCount
		args.isUseDxInput = useDx
		args.pluginOptions = pluginOptions
		args.disabledPlugins = disablePlugins.split(",").map { it.trim() }.toMutableSet()
		return args
	}

	private fun buildEnumSetForRenameFlags(): EnumSet<RenameEnum> {
		val set = EnumSet.noneOf(RenameEnum::class.java)
		set.addAll(renameFlags)
		return set
	}

	/** `logLevel` 字段是 `@JvmField`，这里显式提供与原 Java 同名的 getter/setter。 */
	fun getLogLevel(): LogHelper.LogLevelEnum = logLevel

	fun setLogLevel(logLevel: LogHelper.LogLevelEnum) {
		this.logLevel = logLevel
	}

	/**
	 * 获取“源码名作为类别名”的策略。
	 * 优先使用新参数，其次回退到已废弃的布尔参数，最后用默认值。
	 */
	@Suppress("DEPRECATION")
	fun getUseSourceNameAsClassNameAlias(): UseSourceNameAsClassNameAlias {
		val alias = useSourceNameAsClassNameAlias
		if (alias != null) {
			return alias
		}
		val deprecatedAlias = deobfuscationUseSourceNameAsAlias
		if (deprecatedAlias != null) {
			return UseSourceNameAsClassNameAlias.create(deprecatedAlias)
		}
		return UseSourceNameAsClassNameAlias.getDefault()
	}

	/** 对应原 Java 的 setter（字段本身是 `@JvmField`）。 */
	fun setUseSourceNameAsClassNameAlias(useSourceNameAsClassNameAlias: UseSourceNameAsClassNameAlias) {
		this.useSourceNameAsClassNameAlias = useSourceNameAsClassNameAlias
	}

	/**
	 * @deprecated 改用 [getUseSourceNameAsClassNameAlias]。
	 */
	@Deprecated("Use getUseSourceNameAsClassNameAlias() instead.")
	fun isDeobfuscationUseSourceNameAsAlias(): Boolean = getUseSourceNameAsClassNameAlias().toBoolean()

	fun isRenameCaseSensitive(): Boolean = renameFlags.contains(RenameEnum.CASE)

	fun isRenameValid(): Boolean = renameFlags.contains(RenameEnum.VALID)

	fun isRenamePrintable(): Boolean = renameFlags.contains(RenameEnum.PRINTABLE)

	/** `--rename-flags` 的转换器：把逗号分隔的字符串转为 [RenameEnum] 集合。 */
	internal class RenameConverter(private val paramName: String) : IStringConverter<Set<RenameEnum>> {

		override fun convert(value: String): Set<RenameEnum> {
			if (value.equals("NONE", ignoreCase = true)) {
				return EnumSet.noneOf(RenameEnum::class.java)
			}
			if (value.equals("ALL", ignoreCase = true)) {
				return EnumSet.allOf(RenameEnum::class.java)
			}
			val set = EnumSet.noneOf(RenameEnum::class.java)
			for (s in value.split(",")) {
				try {
					set.add(RenameEnum.valueOf(s.trim().uppercase(Locale.ROOT)))
				} catch (e: Exception) {
					throw JadxArgsValidateException(
						"'" + s + "' is unknown for parameter " + paramName +
							", possible values are " + JadxCLIArgs.enumValuesString(RenameEnum.values()),
					)
				}
			}
			return set
		}
	}

	/**
	 * 枚举转换器基类：把 `--xxx value` 的短横线小写形式转成枚举名。
	 * 保留 [Function]/[Supplier] 参数类型，与原 Java 签名一致。
	 */
	abstract class BaseEnumConverter<E : Enum<E>>(
		private val parse: Function<String, E>,
		private val values: Supplier<Array<E>>,
	) : IStringConverter<E> {

		override fun convert(value: String): E {
			try {
				return parse.apply(stringAsEnumName(value))
			} catch (e: Exception) {
				throw JadxArgsValidateException(
					"'" + value + "' is unknown, possible values are: " + JadxCLIArgs.enumValuesString(values.get()),
				)
			}
		}
	}

	/** 调用图保存格式。 */
	enum class CallGraphSaveMode {
		NONE,
		DOT,
		JSON,
	}

	class CommentsLevelConverter :
		BaseEnumConverter<CommentsLevel>(
			Function { name -> CommentsLevel.valueOf(name) },
			Supplier { CommentsLevel.values() },
		)

	class UseKotlinMethodsForVarNamesConverter :
		BaseEnumConverter<UseKotlinMethodsForVarNames>(
			Function { name -> UseKotlinMethodsForVarNames.valueOf(name) },
			Supplier { UseKotlinMethodsForVarNames.values() },
		)

	class DeobfuscationMapFileModeConverter :
		BaseEnumConverter<GeneratedRenamesMappingFileMode>(
			Function { name -> GeneratedRenamesMappingFileMode.valueOf(name) },
			Supplier { GeneratedRenamesMappingFileMode.values() },
		)

	class ResourceNameSourceConverter :
		BaseEnumConverter<ResourceNameSource>(
			Function { name -> ResourceNameSource.valueOf(name) },
			Supplier { ResourceNameSource.values() },
		)

	class UseSourceNameAsClassNameConverter :
		BaseEnumConverter<UseSourceNameAsClassNameAlias>(
			Function { name -> UseSourceNameAsClassNameAlias.valueOf(name) },
			Supplier { UseSourceNameAsClassNameAlias.values() },
		)

	class DecompilationModeConverter :
		BaseEnumConverter<DecompilationMode>(
			Function { name -> DecompilationMode.valueOf(name) },
			Supplier { DecompilationMode.values() },
		)

	class ExportGradleTypeConverter :
		BaseEnumConverter<ExportGradleType>(
			Function { name -> ExportGradleType.valueOf(name) },
			Supplier { ExportGradleType.values() },
		)

	class LogLevelConverter :
		BaseEnumConverter<LogHelper.LogLevelEnum>(
			Function { name -> LogHelper.LogLevelEnum.valueOf(name) },
			Supplier { LogHelper.LogLevelEnum.values() },
		)

	class IntegerFormatConverter :
		BaseEnumConverter<IntegerFormat>(
			Function { name -> IntegerFormat.valueOf(name) },
			Supplier { IntegerFormat.values() },
		)

	class CallGraphSaveModeConverter :
		BaseEnumConverter<CallGraphSaveMode>(
			Function { name -> CallGraphSaveMode.valueOf(name) },
			Supplier { CallGraphSaveMode.values() },
		)

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxCLIArgs::class.java)

		/**
		 * 解析命令行并（可选）合并配置文件。
		 *
		 * @return 处理后的参数对象；需要提前退出（--help/--version/保存配置等）时返回 null
		 */
		@JvmStatic
		fun <T : JadxCLIArgs> processArgs(args: Array<String>, argsObj: T, configAdapter: JadxConfigAdapter<T>?): T? {
			val jcw = JCommanderWrapper(argsObj)
			if (!jcw.parse(args)) {
				return null
			}
			applyArgs(argsObj)

			// 处理子命令与提前退出标志
			if (!argsObj.process(jcw)) {
				return null
			}
			var resultObj = argsObj
			if (configAdapter != null) {
				if (resultObj.printFiles) {
					printFilesAndDirs(configAdapter.defaultConfigFileName)
					return null
				}
				if (!resultObj.config.equals("none", ignoreCase = true)) {
					// 加载配置文件并与命令行参数合并
					try {
						configAdapter.useConfigRef(resultObj.config)
						val configObj = configAdapter.load()
						if (configObj != null) {
							jcw.overrideProvided(configObj)
							resultObj = configObj
						}
					} catch (e: Exception) {
						LOG.error("Config load failed, continue with default values", e)
					}
				}
			}
			// 校验结果对象
			resultObj.verify()
			applyArgs(resultObj)

			// 如需要则保存配置
			if (resultObj.saveConfig != null) {
				saveConfig(resultObj, configAdapter)
				return null
			}
			return resultObj
		}

		private fun <T : JadxCLIArgs> applyArgs(argsObj: T) {
			// 应用日志级别
			LogHelper.initLogLevel(argsObj)
			LogHelper.applyLogLevels()
		}

		private fun printFilesAndDirs(defaultConfigFileName: String) {
			println("Files and directories used by jadx:")
			println(" - default config file: " + JadxCommonFiles.getConfigDir().resolve(defaultConfigFileName).toAbsolutePath())
			println(" - config directory:    " + JadxCommonFiles.getConfigDir().toAbsolutePath())
			println(" - cache directory:     " + JadxCommonFiles.getCacheDir().toAbsolutePath())
			println(" - temp directory:      " + JadxTempFiles.tempRootDir.parent.toAbsolutePath())
		}

		private fun <T : JadxCLIArgs> saveConfig(argsObj: T, configAdapter: JadxConfigAdapter<T>?) {
			if (configAdapter == null) {
				throw JadxRuntimeException("Config adapter set to null, can't save config")
			}
			configAdapter.useConfigRef(argsObj.saveConfig)
			configAdapter.save(argsObj)
			println("Config saved to " + checkNotNull(configAdapter.configPath).toAbsolutePath())
		}

		/** 把枚举值数组拼成 `case, valid, printable` 形式。 */
		@JvmStatic
		fun enumValuesString(values: Array<out Enum<*>>): String = values.joinToString(", ") { it.name.replace('_', '-').lowercase(Locale.ROOT) }
	}
}

/** [JadxCLIArgs.enumValuesString] 的逆操作：把短横线小写形式转成枚举常量名。 */
private fun stringAsEnumName(value: String): String = value.replace('-', '_').uppercase(Locale.ROOT)
