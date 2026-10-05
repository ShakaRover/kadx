package kadx.core.dex.visitors

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.info.ClassInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.InvokeType
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.args.SSAVar
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.regions.variables.ProcessVariables
import kadx.core.dex.visitors.shrink.CodeShrinkVisitor
import kadx.core.utils.BlockUtils
import kadx.core.utils.exceptions.KadxException
import java.util.Collections

/**
 * 拆箱访问者：移除基本类型的装箱调用。
 *
 * **做什么**：把 `Integer.valueOf(1)` 这类调用替换为常量指令 `const 1`，
 * 并在可行时把结果变量类型从包装类型改回基本类型。
 *
 * **为什么**：DEX 中大量自动装箱会掩盖真实的常量语义，拆箱后常量内联（ConstInline）
 * 才能生效，反编译结果也更接近源码。
 */
@KadxVisitor(
	name = "DeboxingVisitor",
	desc = "Remove primitives boxing",
	runBefore = [
		CodeShrinkVisitor::class,
		ProcessVariables::class,
	],
)
class DeboxingVisitor : AbstractVisitor() {

	private lateinit var valueOfMths: MutableSet<MethodInfo>

	override fun init(root: RootNode) {
		valueOfMths = HashSet()
		valueOfMths.add(valueOfMth(root, ArgType.INT, "java.lang.Integer"))
		valueOfMths.add(valueOfMth(root, ArgType.BOOLEAN, "java.lang.Boolean"))
		valueOfMths.add(valueOfMth(root, ArgType.BYTE, "java.lang.Byte"))
		valueOfMths.add(valueOfMth(root, ArgType.SHORT, "java.lang.Short"))
		valueOfMths.add(valueOfMth(root, ArgType.CHAR, "java.lang.Character"))
		valueOfMths.add(valueOfMth(root, ArgType.LONG, "java.lang.Long"))
	}

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		var replaced = false
		for (blockNode in checkNotNull(mth.basicBlocks)) {
			val insnList = blockNode.instructions
			val count = insnList.size
			for (i in 0 until count) {
				val insnNode = insnList[i]
				if (insnNode.type == InsnType.INVOKE) {
					val replaceInsn = checkForReplace(insnNode as InvokeNode)
					if (replaceInsn != null) {
						BlockUtils.replaceInsn(mth, blockNode, i, replaceInsn)
						replaced = true
					}
				}
			}
		}
		if (replaced) {
			ConstInlineVisitor.process(mth)
		}
	}

	private fun checkForReplace(insnNode: InvokeNode): InsnNode? {
		if (insnNode.invokeType != InvokeType.STATIC || insnNode.result == null) {
			return null
		}
		val callMth = insnNode.callMth
		if (valueOfMths.contains(callMth)) {
			val resArg = checkNotNull(insnNode.result)
			val arg = insnNode.getArg(0)
			if (arg.isLiteral) {
				val primitiveType = callMth.argumentsTypes[0]
				val boxType = callMth.returnType
				if (isNeedExplicitCast(resArg, primitiveType, boxType)) {
					arg.add(AFlag.EXPLICIT_PRIMITIVE_TYPE)
				}
				arg.setType(primitiveType)
				val forbidInline: Boolean
				if (canChangeTypeToPrimitive(resArg, boxType)) {
					resArg.setType(primitiveType)
					forbidInline = false
				} else {
					forbidInline = true
				}

				val constInsn = InsnNode(InsnType.CONST, 1)
				constInsn.addArg(arg)
				constInsn.setResult(resArg)
				if (forbidInline) {
					constInsn.add(AFlag.DONT_INLINE)
				}
				return constInsn
			}
		}
		return null
	}

	private fun isNeedExplicitCast(resArg: RegisterArg, primitiveType: ArgType, boxType: ArgType): Boolean {
		if (primitiveType == ArgType.LONG) {
			return true
		}
		if (primitiveType != ArgType.INT) {
			val useTypes = collectUseTypes(resArg)
			useTypes.add(resArg.getType())
			useTypes.remove(boxType)
			useTypes.remove(primitiveType)
			return useTypes.isNotEmpty()
		}
		return false
	}

	private fun canChangeTypeToPrimitive(arg: RegisterArg, boxType: ArgType): Boolean {
		for (ssaVar in checkNotNull(arg.sVar).codeVar.ssaVars) {
			if (ssaVar.isTypeImmutable()) {
				return false
			}
			val assignInsn = ssaVar.assignInsn ?: return false // 方法参数
			val assignInsnType = assignInsn.type
			if (assignInsnType == InsnType.CONST || assignInsnType == InsnType.MOVE) {
				if (assignInsn.getArg(0).getType().isObject()) {
					return false
				}
			}
			val initType = checkNotNull(assignInsn.result).getInitType()
			if (initType.isObject() && initType != boxType) {
				// 某些相关变量是别的对象类型
				return false
			}

			for (useArg in ssaVar.useList) {
				val parentInsn = useArg.getParentInsn() ?: return false
				if (parentInsn.type == InsnType.INVOKE) {
					val invokeNode = parentInsn as InvokeNode
					if (useArg == invokeNode.getInstanceArg()) {
						return false
					}
				}
			}
		}
		return true
	}

	private fun collectUseTypes(arg: RegisterArg): MutableSet<ArgType> {
		val types = HashSet<ArgType>()
		for (useArg in checkNotNull(arg.sVar).useList) {
			types.add(useArg.getType())
			types.add(useArg.getInitType())
		}
		return types
	}

	companion object {
		private fun valueOfMth(root: RootNode, argType: ArgType, clsName: String): MethodInfo {
			val boxType = ArgType.`object`(clsName)
			val boxCls = ClassInfo.fromType(root, boxType)
			return MethodInfo.fromDetails(root, boxCls, "valueOf", Collections.singletonList(argType), boxType)
		}
	}
}
