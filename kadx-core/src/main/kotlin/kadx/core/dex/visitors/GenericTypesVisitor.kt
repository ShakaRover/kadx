package kadx.core.dex.visitors

import kadx.core.dex.attributes.nodes.GenericInfoAttr
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.mods.ConstructorInsn
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.shrink.CodeShrinkVisitor
import kadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import kadx.core.utils.exceptions.KadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 泛型类型信息挂载访问者。
 *
 * **做什么**：对方法中每个构造器调用指令，若其结果的泛型实参已知，则把泛型信息
 * 以 [GenericInfoAttr] 挂到指令上，供代码生成时输出 `new Foo<String>(...)` 这样的类型。
 *
 * **为什么**：类型推断只能确定原始类型，泛型实参往往来自结果变量的 CodeVar 类型，
 * 需要在构造器指令上单独保存。
 */
@KadxVisitor(
	name = "GenericTypesVisitor",
	desc = "Fix and apply generic type info",
	runAfter = [TypeInferenceVisitor::class],
	runBefore = [CodeShrinkVisitor::class, MethodInvokeVisitor::class],
)
class GenericTypesVisitor : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		for (block in checkNotNull(mth.basicBlocks)) {
			for (insn in block.instructions) {
				if (insn.type == InsnType.CONSTRUCTOR) {
					attachGenericTypesInfo(mth, insn as ConstructorInsn)
				}
			}
		}
	}

	private fun attachGenericTypesInfo(mth: MethodNode, insn: ConstructorInsn) {
		try {
			val resultArg = insn.result ?: return
			val argType = checkNotNull(resultArg.sVar).codeVar.type ?: return
			val genericTypes = argType.getGenericTypes() ?: return
			val cls = mth.root().resolveClass(insn.classType)
			if (cls != null && cls.genericTypeParameters.isEmpty()) {
				return
			}
			insn.addAttr(GenericInfoAttr(genericTypes))
		} catch (e: Exception) {
			LOG.error("Failed to attach constructor generic info", e)
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(GenericTypesVisitor::class.java)
	}
}
