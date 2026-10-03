package jadx.core.dex.visitors

import jadx.api.plugins.input.data.ICatch
import jadx.api.plugins.input.data.ITry
import jadx.api.plugins.utils.Utils
import jadx.core.Consts
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.info.ClassInfo
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.trycatch.CatchAttr
import jadx.core.dex.trycatch.ExcHandlerAttr
import jadx.core.dex.trycatch.ExceptionHandler
import jadx.core.utils.exceptions.JadxException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * try/catch 信息挂载访问者。
 *
 * **做什么**：读取方法原始字节码中的异常表（[ITry]），把它转换成 jadx 内部的
 * [ExceptionHandler]，并在对应的指令区间上打上 [AFlag.TRY_ENTER]/[AFlag.TRY_LEAVE]
 * 与 [AType.EXC_CATCH] 属性。
 *
 * **为什么**：后续的异常区域还原完全依赖这些标记；区间内若没有任何指令，
 * 会插入一条合成 NOP 来承载标记。
 */
@JadxVisitor(
	name = "Attach Try/Catch Visitor",
	desc = "Attach try/catch info to instructions",
	runBefore = [ProcessInstructionsVisitor::class],
)
class AttachTryCatchVisitor : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		initTryCatches(mth, checkNotNull(mth.instructions), checkNotNull(mth.codeReader).tries)
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(AttachTryCatchVisitor::class.java)

		private fun initTryCatches(mth: MethodNode, insnByOffset: Array<InsnNode?>, tries: List<ITry>) {
			if (tries.isEmpty()) {
				return
			}
			if (Consts.DEBUG_EXC_HANDLERS) {
				LOG.debug("Raw try blocks in {}", mth)
				for (tryData in tries) {
					LOG.debug(" - {}", tryData)
				}
			}
			for (tryData in tries) {
				val handlers = convertToHandlers(mth, tryData.catch, insnByOffset)
				if (handlers.isEmpty()) {
					continue
				}
				markTryBounds(insnByOffset, tryData, CatchAttr.build(handlers))
			}
		}

		private fun markTryBounds(insnByOffset: Array<InsnNode?>, aTry: ITry, catchAttr: CatchAttr) {
			var offset = aTry.startOffset
			val end = aTry.endOffset

			var tryBlockStarted = false
			var insn: InsnNode? = null
			while (offset <= end) {
				val insnAtOffset = insnByOffset[offset]
				if (insnAtOffset != null) {
					insn = insnAtOffset
					attachCatchAttr(catchAttr, insn)
					if (!tryBlockStarted) {
						insn.add(AFlag.TRY_ENTER)
						tryBlockStarted = true
					}
				}
				offset = ProcessInstructionsVisitor.getNextInsnOffset(insnByOffset, offset)
				if (offset == -1) {
					break
				}
			}
			if (tryBlockStarted) {
				checkNotNull(insn).add(AFlag.TRY_LEAVE)
			} else {
				// 区间内没有指令 -> 在起始偏移插入一条 NOP
				val nop = insertNOP(insnByOffset, aTry.startOffset)
				nop.add(AFlag.TRY_ENTER)
				nop.add(AFlag.TRY_LEAVE)
				nop.addAttr(catchAttr)
			}
		}

		private fun attachCatchAttr(catchAttr: CatchAttr, insn: InsnNode) {
			val existAttr = insn.get(AType.EXC_CATCH)
			if (existAttr != null) {
				// 合并处理器
				val handlers = Utils.concat(existAttr.handlers, catchAttr.handlers)
				insn.addAttr(CatchAttr.build(ArrayList(handlers)))
			} else {
				insn.addAttr(catchAttr)
			}
		}

		private fun convertToHandlers(mth: MethodNode, catchBlock: ICatch, insnByOffset: Array<InsnNode?>): MutableList<ExceptionHandler> {
			val handlerOffsetArr = catchBlock.handlers
			val handlerTypes = catchBlock.types

			val handlersCount = handlerOffsetArr.size
			val list = ArrayList<ExceptionHandler>(handlersCount)
			for (i in 0 until handlersCount) {
				val handlerOffset = handlerOffsetArr[i]
				val type = ClassInfo.fromName(mth.root(), handlerTypes[i])
				Utils.addToList(list, createHandler(mth, insnByOffset, handlerOffset, type))
			}
			val allHandlerOffset = catchBlock.catchAllHandler
			if (allHandlerOffset >= 0) {
				Utils.addToList(list, createHandler(mth, insnByOffset, allHandlerOffset, null))
			}
			return list
		}

		private fun createHandler(
			mth: MethodNode,
			insnByOffset: Array<InsnNode?>,
			handlerOffset: Int,
			type: ClassInfo?,
		): ExceptionHandler? {
			var insn = insnByOffset[handlerOffset]
			if (insn != null) {
				val excHandlerAttr = insn.get(AType.EXC_HANDLER)
				if (excHandlerAttr != null) {
					val handler = excHandlerAttr.handler
					if (handler.addCatchType(mth, type)) {
						// 已存在的处理器被更新（来自同一个 try 块）—— 不再添加
						return null
					}
					// 同一个处理器（可被不同 try 块复用）
					return handler
				}
			} else {
				insn = insertNOP(insnByOffset, handlerOffset)
			}
			val handler = ExceptionHandler.build(mth, handlerOffset, type)
			mth.addExceptionHandler(handler)
			insn.addAttr(ExcHandlerAttr(handler))
			return handler
		}

		private fun insertNOP(insnByOffset: Array<InsnNode?>, offset: Int): InsnNode {
			val nop = InsnNode(InsnType.NOP, 0)
			nop.setOffset(offset)
			nop.add(AFlag.SYNTHETIC)
			insnByOffset[offset] = nop
			return nop
		}
	}
}
