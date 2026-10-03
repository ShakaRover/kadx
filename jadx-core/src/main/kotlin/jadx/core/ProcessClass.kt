package jadx.core

import jadx.api.DecompilationMode
import jadx.api.ICodeInfo
import jadx.api.JadxArgs
import jadx.api.impl.SimpleCodeInfo
import jadx.core.codegen.CodeGen
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.DecompileModeOverrideAttr
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.LoadStage
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.ProcessState.GENERATED_AND_UNLOADED
import jadx.core.dex.nodes.ProcessState.LOADED
import jadx.core.dex.nodes.ProcessState.NOT_LOADED
import jadx.core.dex.nodes.ProcessState.PROCESS_COMPLETE
import jadx.core.dex.nodes.ProcessState.PROCESS_STARTED
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.DepthTraversal
import jadx.core.dex.visitors.IDexTreeVisitor
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.EnumMap

/**
 * 类处理与代码生成调度器：按 Pass 链处理类、生成代码，并管理类的加载状态。
 *
 * **做什么**：
 * - [generateCode]：处理类及其依赖后生成代码；
 * - [forceProcess] / [forceGenerateCode]：不处理依赖，直接处理 / 生成；
 * - [forceGenerateCodeForMode]：按指定反编译模式生成（用于局部覆盖）；
 * - [processMethodUntilVisitor] / [processMethodToVisitor]：调试用，处理到某个 visitor 为止。
 *
 * **状态机**：`NOT_LOADED → LOADED → PROCESS_STARTED → PROCESS_COMPLETE → GENERATED_AND_UNLOADED`，
 * 通过对 [ClassNode.classInfo] 加锁保证同一类不会被并发处理。
 */
class ProcessClass(
	private val passesList: MutableList<IDexTreeVisitor>,
) {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ProcessClass::class.java)

		/** 表示「未生成代码」的空占位对象。 */
		private val NOT_GENERATED: ICodeInfo = SimpleCodeInfo("")
	}

	/** 按反编译模式缓存的子处理器（惰性创建）。 */
	private val modesMap: MutableMap<DecompilationMode, ProcessClass> = EnumMap(DecompilationMode::class.java)

	/**
	 * 处理单个类（可选生成代码）。
	 *
	 * @param codegen true 表示最后还要生成代码
	 * @return 生成代码时返回代码信息；否则返回 null
	 */
	private fun process(cls: ClassNode, codegen: Boolean): ICodeInfo? {
		if (!codegen && cls.state == PROCESS_COMPLETE) {
			// 已完成处理，无需重复
			return null
		}
		Utils.checkThreadInterrupt()
		synchronized(cls.classInfo) {
			try {
				if (cls.contains(AFlag.CLASS_DEEP_RELOAD)) {
					cls.remove(AFlag.CLASS_DEEP_RELOAD)
					cls.deepUnload()
					cls.add(AFlag.CLASS_UNLOADED)
				}
				if (cls.contains(AFlag.CLASS_UNLOADED)) {
					cls.root().runPreDecompileStageForClass(cls)
					cls.remove(AFlag.CLASS_UNLOADED)
				}
				if (cls.state == GENERATED_AND_UNLOADED) {
					// 强制重新加载代码
					cls.state = NOT_LOADED
				}
				if (codegen) {
					cls.loadStage = LoadStage.CODEGEN_STAGE
					if (cls.contains(AFlag.RELOAD_AT_CODEGEN_STAGE)) {
						cls.remove(AFlag.RELOAD_AT_CODEGEN_STAGE)
						cls.unload()
					}
				} else {
					cls.loadStage = LoadStage.PROCESS_STAGE
				}
				if (cls.state == NOT_LOADED) {
					cls.load()
				}
				if (cls.state == LOADED) {
					cls.state = PROCESS_STARTED
					for (visitor in passesList) {
						DepthTraversal.visit(visitor, cls)
					}
					cls.state = PROCESS_COMPLETE
				}
				if (codegen) {
					Utils.checkThreadInterrupt()
					val code = CodeGen.generate(cls)
					if (!cls.contains(AFlag.DONT_UNLOAD_CLASS)) {
						cls.unload()
						cls.state = GENERATED_AND_UNLOADED
					}
					return code
				}
				return null
			} catch (e: StackOverflowError) {
				if (codegen) {
					throw e
				}
				cls.addError("Class process error: " + e.javaClass.simpleName, e)
				return null
			} catch (e: Exception) {
				if (codegen) {
					throw e
				}
				cls.addError("Class process error: " + e.javaClass.simpleName, e)
				return null
			}
		}
	}

	/** 生成类的代码：先处理其依赖，再处理自身并生成。 */
	fun generateCode(cls: ClassNode): ICodeInfo {
		val topParentClass = cls.topParentClass
		if (topParentClass !== cls) {
			return generateCode(topParentClass)
		}
		try {
			if (cls.contains(AFlag.DONT_GENERATE)) {
				process(cls, false)
				return NOT_GENERATED
			}
			for (depCls in cls.dependencies) {
				process(depCls, false)
			}
			if (cls.codegenDeps.isNotEmpty()) {
				process(cls, false)
				for (codegenDep in cls.codegenDeps) {
					process(codegenDep, false)
				}
			}
			val code = process(cls, true)
			if (code == null) {
				throw JadxRuntimeException("Codegen failed")
			}
			return code
		} catch (e: StackOverflowError) {
			throw JadxRuntimeException("Failed to generate code for class: " + cls.fullName, e)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to generate code for class: " + cls.fullName, e)
		}
	}

	/** 只加载并处理类本身，不处理其依赖。 */
	fun forceProcess(cls: ClassNode) {
		val topParentClass = cls.topParentClass
		if (topParentClass !== cls) {
			forceProcess(topParentClass)
			return
		}
		try {
			process(cls, false)
		} catch (e: StackOverflowError) {
			throw JadxRuntimeException("Failed to process class: " + cls.fullName, e)
		} catch (e: Exception) {
			throw JadxRuntimeException("Failed to process class: " + cls.fullName, e)
		}
	}

	/** 只为类生成代码，不处理其依赖。 */
	fun forceGenerateCode(cls: ClassNode): ICodeInfo? = try {
		process(cls, true)
	} catch (e: StackOverflowError) {
		throw JadxRuntimeException("Failed to generate code for class: " + cls.fullName, e)
	} catch (e: Exception) {
		throw JadxRuntimeException("Failed to generate code for class: " + cls.fullName, e)
	}

	/** 按指定反编译模式生成代码（临时覆盖类的模式，生成后恢复）。 */
	fun forceGenerateCodeForMode(cls: ClassNode, mode: DecompilationMode): ICodeInfo? {
		synchronized(modesMap) {
			val prCls = modesMap.computeIfAbsent(mode) {
				val root = cls.root()
				val newPrCls = ProcessClass(getPassesForMode(root.args, mode))
				newPrCls.initPasses(root)
				newPrCls
			}
			try {
				cls.addAttr(DecompileModeOverrideAttr(mode))
				return prCls.forceGenerateCode(cls)
			} finally {
				cls.remove(AType.DECOMPILE_MODE_OVERRIDE)
			}
		}
	}

	/** 按模式构造 Pass 链：FALLBACK 与 SIMPLE 使用各自链，其余模式不支持。 */
	private fun getPassesForMode(baseArgs: JadxArgs, mode: DecompilationMode): MutableList<IDexTreeVisitor> = when (mode) {
		DecompilationMode.FALLBACK -> Jadx.fallbackPassesList

		DecompilationMode.SIMPLE -> {
			// 复制必要属性到新的 args（与 Jadx.getSimpleModePasses 中的用法保持一致）
			val args = JadxArgs()
			args.isDebugInfo = baseArgs.isDebugInfo
			args.commentsLevel = baseArgs.commentsLevel
			Jadx.getSimpleModePasses(args)
		}

		else -> throw JadxRuntimeException("Unexpected decompilation mode: $mode")
	}

	/** 对所有 Pass 调用一次 [IDexTreeVisitor.init]，单个失败只记错误。 */
	fun initPasses(root: RootNode) {
		for (pass in passesList) {
			try {
				pass.init(root)
			} catch (e: Exception) {
				LOG.error("Visitor init failed: {}", pass.javaClass.simpleName, e)
			}
		}
	}

	/** 处理到指定名字的 visitor（可选是否包含该 visitor），找不到时返回 false。 */
	fun processMethodUntilVisitor(mth: MethodNode, visitorName: String, includeVisitor: Boolean): Boolean {
		var foundPass: IDexTreeVisitor? = null
		var prevPass: IDexTreeVisitor? = null
		for (pass in passesList) {
			if (pass.getName() == visitorName) {
				foundPass = if (includeVisitor) pass else prevPass
				break
			}
			prevPass = pass
		}
		if (foundPass == null) {
			return false
		}
		return processMethodToVisitor(mth, foundPass)
	}

	/** 重新加载方法并处理到指定 visitor；到达目标后返回 true。 */
	fun processMethodToVisitor(mth: MethodNode, lastPassToProcess: IDexTreeVisitor): Boolean {
		synchronized(mth.topParentClass.classInfo) {
			try {
				mth.unload()
				mth.load()
				for (pass in passesList) {
					DepthTraversal.visit(pass, mth)
					if (pass === lastPassToProcess) {
						return true
					}
				}
			} catch (e: Exception) {
				throw JadxRuntimeException("Failed to process method to visitor: " + lastPassToProcess, e)
			}
			return false
		}
	}

	// TODO: make passes list private and not visible
	val passes: MutableList<IDexTreeVisitor> get() = passesList
}
