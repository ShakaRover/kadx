package kadx.core

import kadx.api.CommentsLevel
import kadx.api.DecompilationMode
import kadx.api.KadxArgs
import kadx.core.deobf.DeobfuscatorVisitor
import kadx.core.deobf.SaveDeobfMapping
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.visitors.AdjustForIfMergeVisitor
import kadx.core.dex.visitors.AnonymousClassVisitor
import kadx.core.dex.visitors.ApplyVariableNames
import kadx.core.dex.visitors.AttachCommentsVisitor
import kadx.core.dex.visitors.AttachMethodDetails
import kadx.core.dex.visitors.AttachTryCatchVisitor
import kadx.core.dex.visitors.CheckCode
import kadx.core.dex.visitors.ClassModifier
import kadx.core.dex.visitors.ConstInlineVisitor
import kadx.core.dex.visitors.ConstructorVisitor
import kadx.core.dex.visitors.DeboxingVisitor
import kadx.core.dex.visitors.DotGraphVisitor
import kadx.core.dex.visitors.EnumVisitor
import kadx.core.dex.visitors.ExtractFieldInit
import kadx.core.dex.visitors.FallbackModeVisitor
import kadx.core.dex.visitors.FixSwitchOverEnum
import kadx.core.dex.visitors.GenericTypesVisitor
import kadx.core.dex.visitors.IDexTreeVisitor
import kadx.core.dex.visitors.InitCodeVariables
import kadx.core.dex.visitors.InlineMethods
import kadx.core.dex.visitors.MarkMethodsForInline
import kadx.core.dex.visitors.MethodInvokeVisitor
import kadx.core.dex.visitors.MethodThrowsVisitor
import kadx.core.dex.visitors.MethodVisitor
import kadx.core.dex.visitors.ModVisitor
import kadx.core.dex.visitors.MoveInlineVisitor
import kadx.core.dex.visitors.OverrideMethodVisitor
import kadx.core.dex.visitors.PrepareForCodeGen
import kadx.core.dex.visitors.ProcessAnonymous
import kadx.core.dex.visitors.ProcessInstructionsVisitor
import kadx.core.dex.visitors.ProcessMethodsForInline
import kadx.core.dex.visitors.ReplaceNewArray
import kadx.core.dex.visitors.ShadowFieldVisitor
import kadx.core.dex.visitors.SignatureProcessor
import kadx.core.dex.visitors.SimplifyVisitor
import kadx.core.dex.visitors.blocks.BlockFinisher
import kadx.core.dex.visitors.blocks.BlockProcessor
import kadx.core.dex.visitors.blocks.BlockSplitter
import kadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor
import kadx.core.dex.visitors.debuginfo.DebugInfoAttachVisitor
import kadx.core.dex.visitors.finaly.MarkFinallyVisitor
import kadx.core.dex.visitors.fixaccessmodifiers.FixAccessModifiers
import kadx.core.dex.visitors.gradle.NonFinalResIdsVisitor
import kadx.core.dex.visitors.kotlin.ProcessKotlinInternals
import kadx.core.dex.visitors.prepare.AddAndroidConstants
import kadx.core.dex.visitors.prepare.CollectConstValues
import kadx.core.dex.visitors.regions.CheckRegions
import kadx.core.dex.visitors.regions.CleanRegions
import kadx.core.dex.visitors.regions.IfRegionVisitor
import kadx.core.dex.visitors.regions.LoopRegionVisitor
import kadx.core.dex.visitors.regions.RegionMakerVisitor
import kadx.core.dex.visitors.regions.ReturnVisitor
import kadx.core.dex.visitors.regions.SwitchBreakVisitor
import kadx.core.dex.visitors.regions.SwitchOverStringVisitor
import kadx.core.dex.visitors.regions.variables.ProcessVariables
import kadx.core.dex.visitors.rename.CodeRenameVisitor
import kadx.core.dex.visitors.rename.RenameVisitor
import kadx.core.dex.visitors.rename.SourceFileRename
import kadx.core.dex.visitors.shrink.CodeShrinkVisitor
import kadx.core.dex.visitors.ssa.SSATransform
import kadx.core.dex.visitors.typeinference.FinishTypeInference
import kadx.core.dex.visitors.typeinference.FixTypesVisitor
import kadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import kadx.core.dex.visitors.usage.UsageInfoVisitor
import kadx.core.utils.KadxBuildInfo
import kadx.core.utils.exceptions.KadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 反编译引擎的 Pass（处理步骤）入口。
 *
 * **做什么**：集中定义 kadx 反编译流程中用到的各类 visitor 列表：
 * - [getPreDecompilePassesList]：类加载后、正式反编译前的准备步骤（重命名、去混淆等）；
 * - [getRegionsModePasses]：标准模式（AUTO / RESTRUCTURE）的完整 Pass 链；
 * - [getSimpleModePasses]：简单模式（不做控制流恢复）的 Pass 链；
 * - [getFallbackPassesList]：回退模式（仅反汇编指令）的 Pass 链；
 * - [getPassesList]：按 [KadxArgs] 中的反编译模式选择上述之一。
 *
 * **为什么用 `object` + `@JvmStatic`**：原 Java 类只有静态方法和私有构造器，
 * 被 Java（`KadxDecompiler`、cli、gui、测试）与 Kotlin（`RootNode`、`MethodGen`）
 * 共同调用。转成 Kotlin 单例后，Java 侧 `Kadx.getPassesList(args)`、
 * `Kadx.VERSION_DEV` 写法保持不变。
 */
object Kadx {

	@Suppress("unused")
	private val LOG: Logger = LoggerFactory.getLogger(Kadx::class.java)

	/** 开发版版本号常量。 */
	const val VERSION_DEV = "dev"

	/**
	 * 按反编译模式返回对应的 Pass 链。
	 *
	 * AUTO / RESTRUCTURE 使用完整的区域恢复流程；SIMPLE 只做基础处理；
	 * FALLBACK 仅输出原始指令。
	 */
	@JvmStatic
	fun getPassesList(args: KadxArgs): MutableList<IDexTreeVisitor> = when (args.decompilationMode) {
		DecompilationMode.AUTO, DecompilationMode.RESTRUCTURE -> getRegionsModePasses(args)
		DecompilationMode.SIMPLE -> getSimpleModePasses(args)
		DecompilationMode.FALLBACK -> fallbackPassesList
		else -> throw KadxRuntimeException("Unknown decompilation mode: " + args.decompilationMode)
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
	fun getRegionsModePasses(args: KadxArgs): MutableList<IDexTreeVisitor> {
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

		if (args.useKotlinMethodsForVarNames != KadxArgs.UseKotlinMethodsForVarNames.DISABLE) {
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
	fun getSimpleModePasses(args: KadxArgs): MutableList<IDexTreeVisitor> {
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
	val version: String get() = KadxBuildInfo.getKadxVersion()

	@JvmStatic
	fun isDevVersion(): Boolean = version == VERSION_DEV
}
