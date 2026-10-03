@file:Suppress("ktlint:standard:property-naming")

package jadx.api

import jadx.api.args.GeneratedRenamesMappingFileMode
import jadx.api.args.IntegerFormat
import jadx.api.args.ResourceNameSource
import jadx.api.args.UseSourceNameAsClassNameAlias
import jadx.api.args.UserRenamesMappingsMode
import jadx.api.data.ICodeData
import jadx.api.deobf.IAliasProvider
import jadx.api.deobf.IRenameCondition
import jadx.api.impl.AnnotatedCodeWriter
import jadx.api.impl.InMemoryCodeCache
import jadx.api.plugins.loader.JadxBasePluginLoader
import jadx.api.plugins.loader.JadxPluginLoader
import jadx.api.security.IJadxSecurity
import jadx.api.security.JadxSecurityFlag
import jadx.api.security.impl.JadxSecurity
import jadx.api.usage.IUsageInfoCache
import jadx.api.usage.impl.InMemoryUsageInfoCache
import jadx.core.deobf.DeobfAliasProvider
import jadx.core.deobf.conditions.DeobfWhitelist
import jadx.core.deobf.conditions.JadxRenameConditions
import jadx.core.export.ExportGradleType
import jadx.core.plugins.PluginContext
import jadx.core.plugins.files.IJadxFilesGetter
import jadx.core.plugins.files.TempFilesGetter
import jadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import java.util.ArrayList
import java.util.EnumSet
import java.util.HashMap
import java.util.HashSet
import java.util.function.Function
import java.util.function.Predicate

/**
 * jadx 的全部配置项（公共可变配置对象）。
 *
 * 公共 API：jadx-cli / jadx-gui / 插件都直接读写这些属性。
 * 为保持 Java 调用方零改动，所有字段都声明为 Kotlin 属性（自动生成 `getX()/setX()/isX()`），
 * 常量用 companion + `@JvmField` 暴露为静态字段。
 */
class JadxArgs : Closeable {

	var inputFiles: MutableList<File> = ArrayList(1)

	/** 追加一个输入文件。 */
	fun addInputFile(inputFile: File) {
		this.inputFiles.add(inputFile)
	}

	/** 设置单个输入文件（追加语义，与原 Java 一致）。 */
	fun setInputFile(inputFile: File) {
		addInputFile(inputFile)
	}

	var outDir: File? = null
	var outDirSrc: File? = null
	var outDirRes: File? = null

	var codeCache: ICodeCache = InMemoryCodeCache()

	/**
	 * 使用数据缓存：跨代码重载保存类/方法/字段的使用位置。
	 * 若不需要重载代码，可设置为 [jadx.api.usage.impl.EmptyUsageInfoCache]。
	 */
	var usageInfoCache: IUsageInfoCache = InMemoryUsageInfoCache()

	var codeWriterProvider: Function<JadxArgs, ICodeWriter> = Function { args -> AnnotatedCodeWriter(args) }

	var threadsCount: Int = DEFAULT_THREADS_COUNT
		set(value) {
			// 确保线程数至少为 1
			field = Math.max(1, value)
		}

	var isCfgOutput: Boolean = false
	var isRawCFGOutput: Boolean = false

	var isShowInconsistentCode: Boolean = false

	var isUseImports: Boolean = true
	var isDebugInfo: Boolean = true
	var isInsertDebugLines: Boolean = false
	var isExtractFinally: Boolean = true
	var isInlineAnonymousClasses: Boolean = true
	var isInlineMethods: Boolean = true
	var isAllowInlineKotlinLambda: Boolean = true
	var isMoveInnerClasses: Boolean = true

	var isSkipResources: Boolean = false
	var isSkipSources: Boolean = false
	var isUseHeadersForDetectResourceExtensions: Boolean = false

	/** 按全限定名过滤待处理类的谓词；为 null 表示不过滤。 */
	var classFilter: Predicate<String>? = null

	/** 是否保存被 [classFilter] 接受的类的依赖。 */
	var isIncludeDependencies: Boolean = false

	var userRenamesMappingsPath: Path? = null
	var userRenamesMappingsMode: UserRenamesMappingsMode = UserRenamesMappingsMode.getDefault()

	var isDeobfuscationOn: Boolean = false
	var useSourceNameAsClassNameAlias: UseSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.getDefault()
	var sourceNameRepeatLimit: Int = 10

	var generatedRenamesMappingFile: File? = null
	var generatedRenamesMappingFileMode: GeneratedRenamesMappingFileMode = GeneratedRenamesMappingFileMode.getDefault()
	var resourceNameSource: ResourceNameSource = ResourceNameSource.AUTO

	var deobfuscationMinLength: Int = 0
	var deobfuscationMaxLength: Int = Integer.MAX_VALUE

	/** 不参与去混淆的类/包（以 `.*` 结尾表示包）列表。 */
	var deobfuscationWhitelist: List<String> = DeobfWhitelist.DEFAULT_LIST

	/** 去混淆器与重命名 visitor 使用的别名提供者。 */
	var aliasProvider: IAliasProvider = DeobfAliasProvider()

	/** 去混淆器重命名节点的条件。 */
	var renameCondition: IRenameCondition = JadxRenameConditions.buildDefault()

	var isEscapeUnicode: Boolean = false
	var isReplaceConsts: Boolean = true
	var isRespectBytecodeAccModifiers: Boolean = false
	var exportGradleType: ExportGradleType? = null

	var isRestoreSwitchOverString: Boolean = true

	var isSkipXmlPrettyPrint: Boolean = false

	var isFsCaseSensitive: Boolean = false

	var renameFlags: MutableSet<RenameEnum> = EnumSet.allOf(RenameEnum::class.java)

	var outputFormat: OutputFormatEnum = OutputFormatEnum.JAVA

	var decompilationMode: DecompilationMode = DecompilationMode.AUTO

	var codeData: ICodeData? = null

	var codeNewLineStr: String = DEFAULT_NEW_LINE_STR

	var codeIndentStr: String = DEFAULT_INDENT_STR

	var commentsLevel: CommentsLevel = CommentsLevel.INFO

	var integerFormat: IntegerFormat = IntegerFormat.AUTO

	/**
	 * 每条指令允许的最大类型更新次数（总次数）。
	 * 必须 >= 1，默认 10。
	 */
	var typeUpdatesLimitCount: Int = 10
		set(value) {
			field = Math.max(1, value)
		}

	var isUseDxInput: Boolean = false

	var useKotlinMethodsForVarNames: UseKotlinMethodsForVarNames = UseKotlinMethodsForVarNames.APPLY

	/**
	 * 额外的文件结构信息。
	 * 默认使用临时目录。
	 */
	var filesGetter: IJadxFilesGetter = TempFilesGetter.INSTANCE

	/**
	 * 额外的数据校验与安全检查。
	 */
	var security: IJadxSecurity = JadxSecurity(JadxSecurityFlag.all())

	/**
	 * 不保存文件（可用于性能测试）。
	 */
	var isSkipFilesSave: Boolean = false

	/**
	 * 运行额外的昂贵检查以验证内部不变量与信息完整性。
	 */
	var isRunDebugChecks: Boolean = false

	/**
	 * 需要从处理流程中排除的 pass 列表。
	 */
	val disabledPasses: MutableList<String> = ArrayList()

	var pluginOptions: Map<String, String> = HashMap()

	var disabledPlugins: MutableSet<String> = HashSet()

	var pluginLoader: JadxPluginLoader = JadxBasePluginLoader()

	var isLoadJadxClsSetFile: Boolean = true

	var isFallbackMode: Boolean
		get() = decompilationMode == DecompilationMode.FALLBACK
		set(value) {
			if (value) {
				decompilationMode = DecompilationMode.FALLBACK
			}
		}

	var isDeobfuscationForceSave: Boolean
		get() = generatedRenamesMappingFileMode == GeneratedRenamesMappingFileMode.OVERWRITE
		set(value) {
			if (value) {
				generatedRenamesMappingFileMode = GeneratedRenamesMappingFileMode.OVERWRITE
			}
		}

	@Deprecated("Use getUseSourceNameAsClassNameAlias() instead.")
	var isUseSourceNameAsClassAlias: Boolean
		get() = useSourceNameAsClassNameAlias.toBoolean()
		set(value) {
			useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.create(value)
		}

	var isExportAsGradleProject: Boolean
		get() = exportGradleType != null
		set(value) {
			if (value) {
				if (exportGradleType == null) {
					exportGradleType = ExportGradleType.AUTO
				}
			} else {
				exportGradleType = null
			}
		}

	var isRenameCaseSensitive: Boolean
		get() = renameFlags.contains(RenameEnum.CASE)
		set(value) {
			updateRenameFlag(value, RenameEnum.CASE)
		}

	var isRenameValid: Boolean
		get() = renameFlags.contains(RenameEnum.VALID)
		set(value) {
			updateRenameFlag(value, RenameEnum.VALID)
		}

	var isRenamePrintable: Boolean
		get() = renameFlags.contains(RenameEnum.PRINTABLE)
		set(value) {
			updateRenameFlag(value, RenameEnum.PRINTABLE)
		}

	val isJsonOutput: Boolean
		get() = outputFormat == OutputFormatEnum.JSON
	fun setRootDir(rootDir: File) {
		outDir = rootDir
		outDirSrc = File(rootDir, DEFAULT_SRC_DIR)
		outDirRes = File(rootDir, DEFAULT_RES_DIR)
	}

	override fun close() {
		try {
			codeCache.close()
			usageInfoCache.close()
			pluginLoader.close()
		} catch (e: Exception) {
			LOG.error("Failed to close JadxArgs", e)
		}
	}

	private fun updateRenameFlag(enabled: Boolean, flag: RenameEnum) {
		if (enabled) {
			renameFlags.add(flag)
		} else {
			renameFlags.remove(flag)
		}
	}

	/**
	 * 所有能影响结果代码的选项的哈希值。
	 */
	fun makeCodeArgsHash(decompiler: JadxDecompiler?): String {
		val argStr = "args:" + decompilationMode + isUseImports + isShowInconsistentCode +
			isInlineAnonymousClasses + isInlineMethods + isMoveInnerClasses + isAllowInlineKotlinLambda +
			isDeobfuscationOn + deobfuscationMinLength + deobfuscationMaxLength + deobfuscationWhitelist +
			useSourceNameAsClassNameAlias + sourceNameRepeatLimit +
			resourceNameSource + isUseHeadersForDetectResourceExtensions +
			useKotlinMethodsForVarNames +
			isInsertDebugLines + isExtractFinally +
			isDebugInfo + isEscapeUnicode + isReplaceConsts + isRestoreSwitchOverString +
			isRespectBytecodeAccModifiers + isFsCaseSensitive + renameFlags +
			commentsLevel + isUseDxInput + integerFormat + typeUpdatesLimitCount +
			"|" + buildPluginsHash(decompiler)
		return FileUtils.md5Sum(argStr)
	}

	override fun toString(): String = "JadxArgs{" + "inputFiles=" + inputFiles +
		", outDir=" + outDir +
		", outDirSrc=" + outDirSrc +
		", outDirRes=" + outDirRes +
		", threadsCount=" + threadsCount +
		", decompilationMode=" + decompilationMode +
		", showInconsistentCode=" + isShowInconsistentCode +
		", useImports=" + isUseImports +
		", skipResources=" + isSkipResources +
		", skipSources=" + isSkipSources +
		", includeDependencies=" + isIncludeDependencies +
		", userRenamesMappingsPath=" + userRenamesMappingsPath +
		", userRenamesMappingsMode=" + userRenamesMappingsMode +
		", deobfuscationOn=" + isDeobfuscationOn +
		", generatedRenamesMappingFile=" + generatedRenamesMappingFile +
		", generatedRenamesMappingFileMode=" + generatedRenamesMappingFileMode +
		", resourceNameSource=" + resourceNameSource +
		", useSourceNameAsClassNameAlias=" + useSourceNameAsClassNameAlias +
		", sourceNameRepeatLimit=" + sourceNameRepeatLimit +
		", useKotlinMethodsForVarNames=" + useKotlinMethodsForVarNames +
		", insertDebugLines=" + isInsertDebugLines +
		", extractFinally=" + isExtractFinally +
		", deobfuscationMinLength=" + deobfuscationMinLength +
		", deobfuscationMaxLength=" + deobfuscationMaxLength +
		", deobfuscationWhitelist=" + deobfuscationWhitelist +
		", escapeUnicode=" + isEscapeUnicode +
		", replaceConsts=" + isReplaceConsts +
		", restoreSwitchOverString=" + isRestoreSwitchOverString +
		", respectBytecodeAccModifiers=" + isRespectBytecodeAccModifiers +
		", exportGradleType=" + exportGradleType +
		", skipXmlPrettyPrint=" + isSkipXmlPrettyPrint +
		", fsCaseSensitive=" + isFsCaseSensitive +
		", renameFlags=" + renameFlags +
		", outputFormat=" + outputFormat +
		", commentsLevel=" + commentsLevel +
		", codeCache=" + codeCache +
		", codeWriter=" + codeWriterProvider.apply(this).javaClass.simpleName +
		", useDxInput=" + isUseDxInput +
		", pluginOptions=" + pluginOptions +
		", cfgOutput=" + isCfgOutput +
		", rawCFGOutput=" + isRawCFGOutput +
		", useHeadersForDetectResourceExtensions=" + isUseHeadersForDetectResourceExtensions +
		", typeUpdatesLimitCount=" + typeUpdatesLimitCount +
		'}'

	/** 重命名时使用的过滤标记。 */
	enum class RenameEnum {
		CASE,
		VALID,
		PRINTABLE,
	}

	/** 代码输出格式。 */
	enum class OutputFormatEnum {
		JAVA,
		JSON,
	}

	/** 处理 Kotlin 方法名作为变量名的策略。 */
	enum class UseKotlinMethodsForVarNames {
		DISABLE,
		APPLY,
		APPLY_AND_HIDE,
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(JadxArgs::class.java)

		@JvmField
		val DEFAULT_THREADS_COUNT: Int = Math.max(1, Runtime.getRuntime().availableProcessors() / 2)

		@JvmField
		val DEFAULT_NEW_LINE_STR: String = System.lineSeparator()

		@JvmField
		val DEFAULT_INDENT_STR: String = "    "

		@JvmField
		val DEFAULT_OUT_DIR: String = "jadx-output"

		@JvmField
		val DEFAULT_SRC_DIR: String = "sources"

		@JvmField
		val DEFAULT_RES_DIR: String = "resources"

		private fun buildPluginsHash(decompiler: JadxDecompiler?): String {
			if (decompiler == null) {
				return ""
			}
			return decompiler.getPluginManager().resolvedPluginContexts
				.joinToString(":") { obj: PluginContext -> obj.getInputsHash() }
		}
	}
}
