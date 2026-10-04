package jadx.core

import jadx.api.CommentsLevel
import jadx.api.DecompilationMode
import jadx.api.JadxArgs
import jadx.core.deobf.DeobfuscatorVisitor
import jadx.core.deobf.SaveDeobfMapping
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.visitors.AdjustForIfMergeVisitor
import jadx.core.dex.visitors.AnonymousClassVisitor
import jadx.core.dex.visitors.ApplyVariableNames
import jadx.core.dex.visitors.AttachCommentsVisitor
import jadx.core.dex.visitors.AttachMethodDetails
import jadx.core.dex.visitors.AttachTryCatchVisitor
import jadx.core.dex.visitors.CheckCode
import jadx.core.dex.visitors.ClassModifier
import jadx.core.dex.visitors.ConstInlineVisitor
import jadx.core.dex.visitors.ConstructorVisitor
import jadx.core.dex.visitors.DeboxingVisitor
import jadx.core.dex.visitors.DotGraphVisitor
import jadx.core.dex.visitors.EnumVisitor
import jadx.core.dex.visitors.ExtractFieldInit
import jadx.core.dex.visitors.FallbackModeVisitor
import jadx.core.dex.visitors.FixSwitchOverEnum
import jadx.core.dex.visitors.GenericTypesVisitor
import jadx.core.dex.visitors.IDexTreeVisitor
import jadx.core.dex.visitors.InitCodeVariables
import jadx.core.dex.visitors.InlineMethods
import jadx.core.dex.visitors.MarkMethodsForInline
import jadx.core.dex.visitors.MethodInvokeVisitor
import jadx.core.dex.visitors.MethodThrowsVisitor
import jadx.core.dex.visitors.MethodVisitor
import jadx.core.dex.visitors.ModVisitor
import jadx.core.dex.visitors.MoveInlineVisitor
import jadx.core.dex.visitors.OverrideMethodVisitor
import jadx.core.dex.visitors.PrepareForCodeGen
import jadx.core.dex.visitors.ProcessAnonymous
import jadx.core.dex.visitors.ProcessInstructionsVisitor
import jadx.core.dex.visitors.ProcessMethodsForInline
import jadx.core.dex.visitors.ReplaceNewArray
import jadx.core.dex.visitors.ShadowFieldVisitor
import jadx.core.dex.visitors.SignatureProcessor
import jadx.core.dex.visitors.SimplifyVisitor
import jadx.core.dex.visitors.blocks.BlockFinisher
import jadx.core.dex.visitors.blocks.BlockProcessor
import jadx.core.dex.visitors.blocks.BlockSplitter
import jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor
import jadx.core.dex.visitors.debuginfo.DebugInfoAttachVisitor
import jadx.core.dex.visitors.finaly.MarkFinallyVisitor
import jadx.core.dex.visitors.fixaccessmodifiers.FixAccessModifiers
import jadx.core.dex.visitors.gradle.NonFinalResIdsVisitor
import jadx.core.dex.visitors.kotlin.ProcessKotlinInternals
import jadx.core.dex.visitors.prepare.AddAndroidConstants
import jadx.core.dex.visitors.prepare.CollectConstValues
import jadx.core.dex.visitors.regions.CheckRegions
import jadx.core.dex.visitors.regions.CleanRegions
import jadx.core.dex.visitors.regions.IfRegionVisitor
import jadx.core.dex.visitors.regions.LoopRegionVisitor
import jadx.core.dex.visitors.regions.RegionMakerVisitor
import jadx.core.dex.visitors.regions.ReturnVisitor
import jadx.core.dex.visitors.regions.SwitchBreakVisitor
import jadx.core.dex.visitors.regions.SwitchOverStringVisitor
import jadx.core.dex.visitors.regions.variables.ProcessVariables
import jadx.core.dex.visitors.rename.CodeRenameVisitor
import jadx.core.dex.visitors.rename.RenameVisitor
import jadx.core.dex.visitors.rename.SourceFileRename
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor
import jadx.core.dex.visitors.ssa.SSATransform
import jadx.core.dex.visitors.typeinference.FinishTypeInference
import jadx.core.dex.visitors.typeinference.FixTypesVisitor
import jadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import jadx.core.dex.visitors.usage.UsageInfoVisitor
import jadx.core.utils.JadxBuildInfo
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 反编译引擎的 Pass（处理步骤）入口。
 *
 * **做什么**：集中定义 jadx 反编译流程中用到的各类 visitor 列表：
 * - [getPreDecompilePassesList]：类加载后、正式反编译前的准备步骤（重命名、去混淆等）；
 * - [getRegionsModePasses]：标准模式（AUTO / RESTRUCTURE）的完整 Pass 链；
 * - [getSimpleModePasses]：简单模式（不做控制流恢复）的 Pass 链；
 * - [getFallbackPassesList]：回退模式（仅反汇编指令）的 Pass 链；
 * - [getPassesList]：按 [JadxArgs] 中的反编译模式选择上述之一。
 *
 * **为什么用 `object` + `@JvmStatic`**：原 Java 类只有静态方法和私有构造器，
 * 被 Java（`JadxDecompiler`、cli、gui、测试）与 Kotlin（`RootNode`、`MethodGen`）
 * 共同调用。转成 Kotlin 单例后，Java 侧 `Jadx.getPassesList(args)`、
 * `Jadx.VERSION_DEV` 写法保持不变。
 */
object Jadx {

	@Suppress("unused")
	private val LOG: Logger = LoggerFactory.getLogger(Jadx::class.java)

	/** 开发版版本号常量。 */
	const val VERSION_DEV = "dev"

	/**
	 * 按反编译模式返回对应的 Pass 链。
	 *
	 * AUTO / RESTRUCTURE 使用完整的区域恢复流程；SIMPLE 只做基础处理；
	 * FALLBACK 仅输出原始指令。
	 */
	@JvmStatic
	fun getPassesList(args: JadxArgs): MutableList<IDexTreeVisitor> = when (args.decompilationMode) {
		DecompilationMode.AUTO, DecompilationMode.RESTRUCTURE -> getRegionsModePasses(args)
		DecompilationMode.SIMPLE -> getSimpleModePasses(args)
		DecompilationMode.FALLBACK -> fallbackPassesList
		else -> throw JadxRuntimeException("Unknown decompilation mode: " + args.decompilationMode)
	}

	/**
	 * 正式反编译前的准备步骤：签名处理、注解覆写、Android 常量、重命名/去混淆、使用信息收集等。
	 */
	@JvmStatic
	val preDecompilePassesList: MutableList<IDexTreeVisitor> get() {
		val passes = ArrayList<IDexTreeVisitor>()
		passes.add(SignatureProcessor())
		passes.add(OverrideMethodVisitor())
		passes.add(AddAndroidConstants())

		// 重命名与去混淆
		passes.add(DeobfuscatorVisitor())
		passes.add(SourceFileRename())
		passes.add(RenameVisitor())
		passes.add(SaveDeobfMapping())

		passes.add(UsageInfoVisitor())
		passes.add(CollectConstValues())
		passes.add(ProcessAnonymous())
		passes.add(ProcessMethodsForInline())
		return passes
	}

	/**
	 * 标准（AUTO / RESTRUCTURE）模式的完整 Pass 链。
	 *
	 * 顺序大致为：指令 IR → 基本块 IR → SSA/类型推断 → 区域恢复 → 变量处理 → 代码生成准备。
	 */
	@JvmStatic
	fun getRegionsModePasses(args: JadxArgs): MutableList<IDexTreeVisitor> {
		val passes = ArrayList<IDexTreeVisitor>()
		// 指令 IR
		passes.add(CheckCode())
		if (args.isDebugInfo) {
			passes.add(DebugInfoAttachVisitor())
		}
		passes.add(AttachTryCatchVisitor())
		if (args.commentsLevel != CommentsLevel.NONE) {
			passes.add(AttachCommentsVisitor())
		}
		passes.add(AttachMethodDetails())
		passes.add(ProcessInstructionsVisitor())

		// 基本块 IR
		passes.add(BlockSplitter())
		passes.add(BlockProcessor())
		passes.add(BlockFinisher())
		if (args.isRawCFGOutput) {
			passes.add(DotGraphVisitor.dumpRaw())
		}

		passes.add(SSATransform())
		passes.add(MoveInlineVisitor())
		passes.add(ConstructorVisitor())
		passes.add(InitCodeVariables())
		if (args.isExtractFinally) {
			passes.add(MarkFinallyVisitor())
		}
		passes.add(ConstInlineVisitor())
		passes.add(TypeInferenceVisitor())
		if (args.isDebugInfo) {
			passes.add(DebugInfoApplyVisitor())
		}
		passes.add(FixTypesVisitor())
		passes.add(FinishTypeInference())

		passes.add(AdjustForIfMergeVisitor())

		if (args.useKotlinMethodsForVarNames != JadxArgs.UseKotlinMethodsForVarNames.DISABLE) {
			passes.add(ProcessKotlinInternals())
		}
		passes.add(CodeRenameVisitor())
		if (args.isInlineMethods) {
			passes.add(InlineMethods())
		}
		passes.add(GenericTypesVisitor())
		passes.add(ShadowFieldVisitor())
		passes.add(DeboxingVisitor())
		passes.add(AnonymousClassVisitor())
		passes.add(ModVisitor())
		passes.add(CodeShrinkVisitor())
		passes.add(ReplaceNewArray())
		if (args.isCfgOutput) {
			passes.add(DotGraphVisitor.dump())
		}

		// 区域 IR
		passes.add(RegionMakerVisitor())
		passes.add(IfRegionVisitor())
		if (args.isRestoreSwitchOverString) {
			passes.add(SwitchOverStringVisitor())
		}
		passes.add(ReturnVisitor())
		passes.add(CleanRegions())

		passes.add(MethodThrowsVisitor())

		passes.add(CodeShrinkVisitor())
		passes.add(MethodInvokeVisitor())
		passes.add(SimplifyVisitor())
		passes.add(CheckRegions())

		passes.add(EnumVisitor())
		passes.add(FixSwitchOverEnum())
		passes.add(NonFinalResIdsVisitor())
		passes.add(ExtractFieldInit())
		passes.add(FixAccessModifiers())
		passes.add(ClassModifier())
		passes.add(LoopRegionVisitor())
		passes.add(SwitchBreakVisitor())

		if (args.isInlineMethods) {
			passes.add(MarkMethodsForInline())
		}
		passes.add(ProcessVariables())
		passes.add(ApplyVariableNames())

		passes.add(PrepareForCodeGen())
		if (args.isCfgOutput) {
			passes.add(DotGraphVisitor.dumpRegions())
		}
		return passes
	}

	/**
	 * 简单（SIMPLE）模式 Pass 链：不做控制流区域恢复，直接输出带 goto 的线性代码。
	 */
	@JvmStatic
	fun getSimpleModePasses(args: JadxArgs): MutableList<IDexTreeVisitor> {
		val passes = ArrayList<IDexTreeVisitor>()
		if (args.isDebugInfo) {
			passes.add(DebugInfoAttachVisitor())
		}
		passes.add(AttachTryCatchVisitor())
		if (args.commentsLevel != CommentsLevel.NONE) {
			passes.add(AttachCommentsVisitor())
		}
		passes.add(AttachMethodDetails())
		passes.add(ProcessInstructionsVisitor())

		passes.add(BlockSplitter())
		if (args.isRawCFGOutput) {
			passes.add(DotGraphVisitor.dumpRaw())
		}
		passes.add(MethodVisitor("DisableBlockLock") { mth -> mth.add(AFlag.DISABLE_BLOCKS_LOCK) })
		passes.add(BlockProcessor())
		passes.add(SSATransform())
		passes.add(MoveInlineVisitor())
		passes.add(ConstructorVisitor())
		passes.add(InitCodeVariables())
		passes.add(ConstInlineVisitor())
		passes.add(TypeInferenceVisitor())
		if (args.isDebugInfo) {
			passes.add(DebugInfoApplyVisitor())
		}
		passes.add(FixTypesVisitor())
		passes.add(FinishTypeInference())
		passes.add(CodeRenameVisitor())
		passes.add(DeboxingVisitor())
		passes.add(ModVisitor())
		passes.add(CodeShrinkVisitor())
		passes.add(ReplaceNewArray())
		passes.add(SimplifyVisitor())
		passes.add(MethodVisitor("ForceGenerateAll") { mth -> mth.remove(AFlag.DONT_GENERATE) })
		if (args.isCfgOutput) {
			passes.add(DotGraphVisitor.dump())
		}
		return passes
	}

	/**
	 * 回退（FALLBACK）模式 Pass 链：不恢复控制流，只反汇编指令。
	 */
	@JvmStatic
	val fallbackPassesList: MutableList<IDexTreeVisitor> get() {
		val passes = ArrayList<IDexTreeVisitor>()
		passes.add(AttachTryCatchVisitor())
		passes.add(AttachCommentsVisitor())
		passes.add(ProcessInstructionsVisitor())
		passes.add(FallbackModeVisitor())
		return passes
	}

	@JvmStatic
	val version: String get() = JadxBuildInfo.getJadxVersion()

	@JvmStatic
	fun isDevVersion(): Boolean = version == VERSION_DEV
}
