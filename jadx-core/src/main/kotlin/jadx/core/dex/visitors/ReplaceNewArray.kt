package jadx.core.dex.visitors

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.nodes.CodeFeaturesAttr
import jadx.core.dex.attributes.nodes.CodeFeaturesAttr.CodeFeature
import jadx.core.dex.instructions.FilledNewArrayNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.NewArrayNode
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.IFieldInfoRef
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor
import jadx.core.utils.InsnList
import jadx.core.utils.InsnRemover
import jadx.core.utils.InsnUtils
import jadx.core.utils.exceptions.JadxException
import java.util.ArrayList
import java.util.Collections
import java.util.IdentityHashMap
import java.util.TreeMap

/**
 * 把 `new-array` + 连续 `aput` 的序列合并成一条 `filled-new-array` 指令。
 *
 * **做什么**：当一个新建数组的每个下标都被常量写入覆盖时，直接生成“填充好的数组”，
 * 使反编译结果里出现 `new int[]{1, 2, 3}` 而不是先声明再逐个赋值。
 * 对 primitive 数组允许缺省部分下标（补 0）。
 *
 * **为什么循环处理**：合并后代码结构变化，需要收缩（[CodeShrinkVisitor]）并重复扫描，
 * 直到没有新的可合并点（带迭代次数上限防止死循环）。
 */
@JadxVisitor(
	name = "ReplaceNewArray",
	desc = "Replace new-array and sequence of array-put to new filled-array instruction",
	runAfter = [CodeShrinkVisitor::class],
)
class ReplaceNewArray : AbstractVisitor() {

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		if (!CodeFeaturesAttr.contains(mth, CodeFeature.NEW_ARRAY)) {
			return
		}
		val remover = InsnRemover(mth)
		var k = 0
		while (true) {
			var changed = false
			for (block in checkNotNull(mth.getBasicBlocks())) {
				val insnList = block.getInstructions()
				val size = insnList.size
				for (i in 0 until size) {
					changed = processInsn(mth, insnList, i, remover) || changed
				}
				remover.performForBlock(block)
			}
			if (changed) {
				CodeShrinkVisitor.shrinkMethod(mth)
			} else {
				break
			}
			if (k++ > 100) {
				mth.addWarnComment("Reached limit for ReplaceNewArray iterations")
				break
			}
		}
	}

	companion object {
		private fun processInsn(mth: MethodNode, instructions: List<InsnNode>, i: Int, remover: InsnRemover): Boolean {
			val insn = instructions[i]
			if (insn.getType() == InsnType.NEW_ARRAY && !insn.contains(AFlag.REMOVE)) {
				return processNewArray(mth, insn as NewArrayNode, instructions, remover)
			}
			return false
		}

		private fun processNewArray(
			mth: MethodNode,
			newArrayInsn: NewArrayNode,
			instructions: List<InsnNode>,
			remover: InsnRemover,
		): Boolean {
			val arrayLenConst = InsnUtils.getConstValueByArg(mth.root(), newArrayInsn.getArg(0))
			if (arrayLenConst !is LiteralArg) {
				return false
			}
			val len = arrayLenConst.literal.toInt()
			if (len == 0) {
				return false
			}
			val arrType = newArrayInsn.getArrayType()
			val elemType = checkNotNull(arrType.getArrayElement())
			val allowMissingKeys = arrType.getArrayDimension() == 1 && elemType.isPrimitive()
			val minLen = if (allowMissingKeys) len / 2 else len

			val arrArg = newArrayInsn.getResult()
			val useList = checkNotNull(checkNotNull(arrArg).sVar).getUseList()
			if (useList.size < minLen) {
				return false
			}
			// 快速检查是否真的用到 APUT
			var foundPut = false
			for (registerArg in useList) {
				val parentInsn = registerArg.getParentInsn()
				if (parentInsn != null && parentInsn.getType() == InsnType.APUT) {
					foundPut = true
					break
				}
			}
			if (!foundPut) {
				return false
			}
			// 收集按下标排序的 put 指令
			val arrPuts = TreeMap<Long, InsnNode>()
			var firstNotAPutUsage: InsnNode? = null
			for (registerArg in useList) {
				val parentInsn = registerArg.getParentInsn()
				if (parentInsn == null ||
					parentInsn.getType() != InsnType.APUT ||
					!arrArg.sameRegAndSVar(parentInsn.getArg(0))
				) {
					if (firstNotAPutUsage == null) {
						firstNotAPutUsage = parentInsn
					}
					continue
				}
				val constVal = InsnUtils.getConstValueByArg(mth.root(), parentInsn.getArg(1))
				if (constVal !is LiteralArg) {
					return false
				}
				val index = constVal.literal
				if (index >= len) {
					return false
				}
				if (arrPuts.containsKey(index)) {
					// 遇到下标重写则停止
					break
				}
				arrPuts[index] = parentInsn
			}
			if (arrPuts.size < minLen) {
				return false
			}
			if (!verifyPutInsns(arrArg, instructions, arrPuts)) {
				return false
			}

			// 检查通过，开始替换
			val filledArr = FilledNewArrayNode(elemType, len)
			filledArr.setResult(arrArg.duplicate())
			filledArr.copyAttributesFrom(newArrayInsn)
			filledArr.inheritMetadata(newArrayInsn)
			filledArr.setOffset(newArrayInsn.getOffset())

			var prevIndex = -1L
			for ((index, put) in arrPuts) {
				if (index != prevIndex) {
					// 缺失的下标用 0 补齐
					var i = prevIndex + 1
					while (i < index) {
						filledArr.addArg(InsnArg.lit(0, elemType))
						i++
					}
				}
				filledArr.addArg(replaceConstInArg(mth, put.getArg(2)))
				remover.addAndUnbind(put)
				prevIndex = index
			}
			// 补齐末尾缺失的 0
			var i = prevIndex + 1
			while (i < len) {
				filledArr.addArg(InsnArg.lit(0, elemType))
				i++
			}
			remover.addAndUnbind(newArrayInsn)

			// 把新指令放在最后一个 put 处，或第一个非 put 使用之前
			val lastPut = checkNotNull(arrPuts[arrPuts.lastKey()])
			var newInsnPos = InsnList.getIndex(instructions, lastPut)
			if (firstNotAPutUsage != null) {
				val idx = InsnList.getIndex(instructions, firstNotAPutUsage)
				if (idx != -1) {
					// TODO: 检查所有参数都已赋值
					newInsnPos = minOf(idx, newInsnPos)
				}
			}
			(instructions as MutableList<InsnNode>).add(newInsnPos, filledArr)
			return true
		}

		private fun verifyPutInsns(arrReg: RegisterArg, insnList: List<InsnNode>, arrPuts: Map<Long, InsnNode>): Boolean {
			val puts = ArrayList(arrPuts.values)
			val putsCount = puts.size
			// 期望所有 put 都在同一个块里
			if (insnList.size < putsCount) {
				return false
			}
			val insnSet: MutableSet<InsnNode> = Collections.newSetFromMap(IdentityHashMap())
			insnSet.addAll(insnList)
			if (!insnSet.containsAll(puts)) {
				return false
			}
			// 数组参数不应出现在 put 指令本身
			for (put in puts) {
				val putArg = put.getArg(2)
				if (putArg.isUseVar(arrReg)) {
					return false
				}
			}
			return true
		}

		private fun replaceConstInArg(mth: MethodNode, valueArg: InsnArg): InsnArg {
			if (valueArg.isLiteral) {
				val f: IFieldInfoRef? = mth.parentClass.getConstFieldByLiteralArg(valueArg as LiteralArg)
				if (f != null) {
					val fGet = IndexInsnNode(InsnType.SGET, f.getFieldInfo(), 0)
					val arg = InsnArg.wrapArg(fGet)
					ModVisitor.addFieldUsage(f, mth)
					return arg
				}
			}
			return valueArg.duplicate()
		}
	}
}
