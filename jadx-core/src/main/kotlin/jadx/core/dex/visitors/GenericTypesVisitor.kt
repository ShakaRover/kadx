package jadx.core.dex.visitors

import jadx.core.dex.attributes.nodes.GenericInfoAttr
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor
import jadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import jadx.core.utils.exceptions.JadxException
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
@JadxVisitor(
	name = "GenericTypesVisitor",
	desc = "Fix and apply generic type info",
	runAfter = [TypeInferenceVisitor::class],
	runBefore = [CodeShrinkVisitor::class, MethodInvokeVisitor::class],
)
class GenericTypesVisitor : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		for (block in checkNotNull(mth.getBasicBlocks())) {
			for (insn in block.getInstructions()) {
				if (insn.getType() == InsnType.CONSTRUCTOR) {
					attachGenericTypesInfo(mth, insn as ConstructorInsn)
				}
			}
		}
	}

	private fun attachGenericTypesInfo(mth: MethodNode, insn: ConstructorInsn) {
		try {
			val resultArg = insn.getResult() ?: return
			val argType = checkNotNull(resultArg.sVar).codeVar.type ?: return
			val genericTypes = argType.getGenericTypes() ?: return
			val cls = mth.root().resolveClass(insn.getClassType())
			if (cls != null && cls.getGenericTypeParameters().isEmpty()) {
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
