package jadx.core.dex.visitors

import jadx.core.codegen.json.JsonMappingGen
import jadx.core.dex.attributes.AType
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.trycatch.CatchAttr
import jadx.core.utils.exceptions.JadxException

/**
 * 兜底模式访问者。
 *
 * **做什么**：当开启 JSON 映射输出时，先导出映射信息；随后清理“不可能抛异常”的指令上
 * 误挂的 try/catch 属性。
 *
 * **为什么**：某些指令（返回、跳转、常量、比较等）本身不会抛异常，保留 EXC_CATCH 只会
 * 干扰后续的 try/catch 还原与代码生成，因此在兜底阶段统一移除。
 */
class FallbackModeVisitor : AbstractVisitor() {

	override fun init(root: RootNode) {
		if (root.getArgs().isJsonOutput) {
			JsonMappingGen.dump(root)
		}
	}

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		for (insn in checkNotNull(mth.instructions)) {
			if (insn == null) {
				continue
			}
			// 移除“不会抛异常”的指令上的异常捕获属性
			val catchAttr: CatchAttr? = insn.get(AType.EXC_CATCH)
			if (catchAttr != null) {
				when (insn.type) {
					InsnType.RETURN,
					InsnType.IF,
					InsnType.GOTO,
					InsnType.JAVA_JSR,
					InsnType.MOVE,
					InsnType.MOVE_EXCEPTION,
					InsnType.ARITH,
					InsnType.NEG,
					InsnType.CONST,
					InsnType.CONST_STR,
					InsnType.CONST_CLASS,
					InsnType.CMP_L,
					InsnType.CMP_G,
					-> insn.remove(AType.EXC_CATCH)

					else -> {}
				}
			}
		}
	}
}
