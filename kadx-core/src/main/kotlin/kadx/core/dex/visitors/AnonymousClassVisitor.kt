package kadx.core.dex.visitors

import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.FieldReplaceAttr
import kadx.core.dex.attributes.nodes.SkipMethodArgsAttr
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.IndexInsnNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.instructions.mods.ConstructorInsn
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.shrink.CodeShrinkVisitor
import kadx.core.utils.exceptions.KadxException
import java.util.Collections
import java.util.LinkedHashMap

/**
 * 匿名内部类预处理访问者。
 *
 * **做什么**：识别匿名类构造器，把它对外部变量的赋值（IPUT）转换为字段替换属性，
 * 使后续 Pass 能把这些字段访问内联掉，从而还原匿名类内联后的代码。
 *
 * **为什么**：javac 会把匿名类捕获的外部变量存成合成字段，若不处理，反编译结果里
 * 会出现大量无意义的合成字段与构造器参数。
 */
@KadxVisitor(
	name = "AnonymousClassVisitor",
	desc = "Prepare anonymous class for inline",
	runBefore = [
		ModVisitor::class,
		CodeShrinkVisitor::class,
	],
)
class AnonymousClassVisitor : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		if (cls.contains(AType.ANONYMOUS_CLASS)) {
			for (mth in cls.methods) {
				if (mth.contains(AFlag.ANONYMOUS_CONSTRUCTOR)) {
					processAnonymousConstructor(mth)
					break
				}
			}
		}
		return true
	}

	companion object {

		private fun processAnonymousConstructor(mth: MethodNode) {
			val usedInsns = ArrayList<InsnNode>()
			val argsMap = getArgsToFieldsMapping(mth, usedInsns)
			if (argsMap.isEmpty()) {
				mth.add(AFlag.NO_SKIP_ARGS)
			} else {
				for ((arg, field) in argsMap) {
					field.addAttr(FieldReplaceAttr(arg))
					field.add(AFlag.DONT_GENERATE)
					if (arg.isRegister) {
						arg.add(AFlag.SKIP_ARG)
						SkipMethodArgsAttr.skipArg(mth, arg as RegisterArg)
					}
				}
			}
			for (usedInsn in usedInsns) {
				usedInsn.add(AFlag.DONT_GENERATE)
			}
		}

		private fun getArgsToFieldsMapping(mth: MethodNode, usedInsns: MutableList<InsnNode>): Map<InsnArg, FieldNode> {
			val callMth: MethodInfo = mth.methodInfo
			val cls = mth.parentClass
			val argList = mth.argRegs
			val outerCls = mth.useIn[0].parentClass
			var startArg = 0
			if (callMth.argsCount != 0 && callMth.argumentsTypes[0] == outerCls.classInfo.type) {
				startArg = 1
			}
			val map = LinkedHashMap<InsnArg, FieldNode>()
			val argsCount = argList.size
			for (i in startArg until argsCount) {
				val arg = argList[i]
				val useInsn = getParentInsnSkipMove(arg) ?: return Collections.emptyMap()
				when (useInsn.type) {
					InsnType.IPUT -> {
						val fieldNode = cls.searchField((useInsn as IndexInsnNode).index as FieldInfo)
						if (fieldNode == null || !fieldNode.accessFlags.isSynthetic()) {
							return Collections.emptyMap()
						}
						map[arg] = fieldNode
						usedInsns.add(useInsn)
					}

					InsnType.CONSTRUCTOR -> {
						val superConstr = useInsn as ConstructorInsn
						if (!superConstr.isSuper) {
							return Collections.emptyMap()
						}
						usedInsns.add(useInsn)
					}

					else -> return Collections.emptyMap()
				}
			}
			return map
		}

		private fun getParentInsnSkipMove(arg: RegisterArg): InsnNode? {
			val sVar = checkNotNull(arg.sVar)
			if (sVar.useCount != 1) {
				return null
			}
			val useArg = sVar.useList[0]
			val parentInsn = useArg.getParentInsn() ?: return null
			if (parentInsn.type == InsnType.MOVE) {
				return getParentInsnSkipMove(checkNotNull(parentInsn.result))
			}
			return parentInsn
		}
	}
}
