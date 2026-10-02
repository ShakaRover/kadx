package jadx.core.dex.visitors.kotlin

import jadx.api.JadxArgs.UseKotlinMethodsForVarNames
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.InitCodeVariables
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor
import jadx.core.dex.visitors.rename.CodeRenameVisitor
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.HashSet

/**
 * 从 Kotlin 编译器生成的 `Intrinsics.checkNotNullParameter(...)` 等调用中提取变量名。
 *
 * **背景**：Kotlin 编译器在方法入口插入 `Intrinsics.checkNotNullParameter(this, "name")`
 * 之类的调用，第二个参数是原始参数名。反编译时这些调用只是噪声，但参数名很有价值。
 *
 * **做什么**：定位 `kotlin.jvm.internal.Intrinsics` 类中负责“变量名”的重载方法，
 * 扫描所有 INVOKE 指令，取出常量字符串参数作为变量名写入 SSA 变量；若配置为
 * `APPLY_AND_HIDE`，则把该指令标记为不生成。
 *
 * **Kotlin 转换说明**：静态常量与静态工具方法放 companion；`trimName` 里的 `$` 需转义；
 * 引用比较用 `===`；字符串比较用 `==`。
 */
@JadxVisitor(
	name = "ProcessKotlinInternals",
	desc = "Use variable names from Kotlin intrinsic1 methods",
	runAfter = [
		InitCodeVariables::class,
		DebugInfoApplyVisitor::class,
	],
	runBefore = [
		CodeRenameVisitor::class,
	],
)
class ProcessKotlinInternals : AbstractVisitor() {

	private var kotlinIntrinsicsCls: ClassInfo? = null

	private var kotlinVarNameSourceMethods: Set<MethodInfo> = emptySet()

	private var hideInsns = false

	@Throws(JadxException::class)
	override fun init(root: RootNode) {
		val kotlinCls = searchKotlinIntrinsicsClass(root)
		if (kotlinCls != null) {
			kotlinIntrinsicsCls = kotlinCls.classInfo
			kotlinVarNameSourceMethods = collectMethods(kotlinCls)
			LOG.debug("Kotlin Intrinsics class: {}, methods: {}", kotlinCls, kotlinVarNameSourceMethods.size)
		} else {
			kotlinIntrinsicsCls = null
			LOG.debug("Kotlin Intrinsics class not found")
		}
		hideInsns = root.getArgs().useKotlinMethodsForVarNames == UseKotlinMethodsForVarNames.APPLY_AND_HIDE
	}

	override fun visit(cls: ClassNode): Boolean {
		if (kotlinIntrinsicsCls == null) {
			return false
		}
		for (mth in cls.methods) {
			processMth(mth)
		}
		return true
	}

	private fun processMth(mth: MethodNode) {
		if (mth.isNoCode() || mth.contains(AType.JADX_ERROR)) {
			return
		}
		for (block in checkNotNull(mth.getBasicBlocks())) {
			for (insn in block.instructions) {
				if (insn.getType() == InsnType.INVOKE) {
					try {
						processInvoke(mth, insn)
					} catch (e: Exception) {
						mth.addWarnComment("Failed to extract var names", e)
					}
				}
			}
		}
	}

	private fun processInvoke(mth: MethodNode, insn: InsnNode) {
		val argsCount = insn.getArgsCount()
		if (argsCount < 2) {
			return
		}
		val invokeMth = (insn as InvokeNode).callMth
		if (!kotlinVarNameSourceMethods.contains(invokeMth)) {
			return
		}
		val firstArg = insn.getArg(0)
		if (!firstArg.isRegister) {
			return
		}
		val varArg = firstArg as RegisterArg
		var renamed = false
		if (argsCount == 2) {
			val str = getConstString(mth, insn, 1)
			if (str != null) {
				renamed = checkAndRename(varArg, str)
			}
		} else if (argsCount == 3) {
			// TODO: 用第二个参数重命名类
			val str = getConstString(mth, insn, 2)
			if (str != null) {
				renamed = checkAndRename(varArg, str)
			}
		}
		if (renamed && hideInsns) {
			insn.add(AFlag.DONT_GENERATE)
		}
	}

	private fun checkAndRename(arg: RegisterArg, str: String): Boolean {
		val name = trimName(str)
		if (NameMapper.isValidAndPrintable(name)) {
			checkNotNull(arg.sVar).codeVar.name = name
			return true
		}
		return false
	}

	private fun getConstString(mth: MethodNode, insn: InsnNode, arg: Int): String? {
		val strArg = insn.getArg(arg)
		if (!strArg.isInsnWrap) {
			return null
		}
		val constInsn = (strArg as InsnWrapArg).wrapInsn
		val insnType = constInsn.getType()
		if (insnType == InsnType.CONST_STR) {
			return (constInsn as ConstStringNode).getString()
		}
		if (insnType == InsnType.SGET) {
			// 还原被内联的常量字段 :(
			val fieldInfo = (constInsn as IndexInsnNode).index as FieldInfo
			val fieldNode = mth.root().resolveField(fieldInfo)
			if (fieldNode != null) {
				val str = checkNotNull(fieldNode.get(JadxAttrType.CONSTANT_VALUE)).value as String?
				val newArg = InsnArg.wrapArg(ConstStringNode(str))
				insn.replaceArg(strArg, newArg)
				return str
			}
		}
		return null
	}

	private fun trimName(str: String): String {
		if (str.startsWith("\$this\$")) {
			return str.substring(6)
		}
		if (str.startsWith("\$")) {
			return str.substring(1)
		}
		return str
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ProcessKotlinInternals::class.java)

		private const val KOTLIN_INTERNAL_PKG = "kotlin.jvm.internal."
		private const val KOTLIN_INTRINSICS_CLS_SHORT_NAME = "Intrinsics"
		private const val KOTLIN_INTRINSICS_CLS: String = KOTLIN_INTERNAL_PKG + KOTLIN_INTRINSICS_CLS_SHORT_NAME
		private const val KOTLIN_VARNAME_SOURCE_MTH1 = "(Ljava/lang/Object;Ljava/lang/String;)V"
		private const val KOTLIN_VARNAME_SOURCE_MTH2 = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V"

		private fun searchKotlinIntrinsicsClass(root: RootNode): ClassNode? {
			val kotlinCls = root.resolveClass(KOTLIN_INTRINSICS_CLS)
			if (kotlinCls != null) {
				return kotlinCls
			}
			val candidates = ArrayList<ClassNode>()
			for (cls in root.getClasses()) {
				if (isKotlinIntrinsicsClass(cls)) {
					candidates.add(cls)
				}
			}
			return Utils.getOne(candidates)
		}

		private fun isKotlinIntrinsicsClass(cls: ClassNode): Boolean {
			val clsInfo = cls.classInfo
			if (clsInfo.aliasShortName == KOTLIN_INTRINSICS_CLS_SHORT_NAME &&
				clsInfo.aliasFullName == KOTLIN_INTRINSICS_CLS
			) {
				return true
			}
			if (!clsInfo.fullName.startsWith(KOTLIN_INTERNAL_PKG)) {
				return false
			}
			if (cls.methods.size < 5) {
				return false
			}
			var mthCount = 0
			for (mth in cls.methods) {
				if (mth.accessFlags.isStatic() &&
					mth.getMethodInfo().shortId.endsWith(KOTLIN_VARNAME_SOURCE_MTH1)
				) {
					mthCount++
				}
			}
			return mthCount > 2
		}

		private fun collectMethods(kotlinCls: ClassNode): Set<MethodInfo> {
			val set = HashSet<MethodInfo>()
			for (mth in kotlinCls.methods) {
				if (!mth.accessFlags.isStatic()) {
					continue
				}
				val shortId = mth.getMethodInfo().shortId
				if (shortId.endsWith(KOTLIN_VARNAME_SOURCE_MTH1) || shortId.endsWith(KOTLIN_VARNAME_SOURCE_MTH2)) {
					set.add(mth.getMethodInfo())
				}
			}
			return set
		}
	}
}
