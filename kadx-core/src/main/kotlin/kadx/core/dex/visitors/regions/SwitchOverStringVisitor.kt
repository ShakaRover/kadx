package kadx.core.dex.visitors.regions

import kadx.api.plugins.input.data.annotations.EncodedType
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.nodes.CodeFeaturesAttr
import kadx.core.dex.attributes.nodes.CodeFeaturesAttr.CodeFeature
import kadx.core.dex.instructions.IfNode
import kadx.core.dex.instructions.IfOp
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.InvokeNode
import kadx.core.dex.instructions.SwitchInsn
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.instructions.args.LiteralArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.IContainer
import kadx.core.dex.nodes.IRegion
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.regions.SwitchRegion
import kadx.core.dex.regions.conditions.IfRegion
import kadx.core.dex.visitors.AbstractVisitor
import kadx.core.dex.visitors.KadxVisitor
import kadx.core.utils.BlockUtils
import kadx.core.utils.InsnRemover
import kadx.core.utils.InsnUtils
import kadx.core.utils.ListUtils
import kadx.core.utils.RegionUtils
import java.util.Collections

/** default 分支的数值哨兵 */
private const val DEFAULT_NUM_VALUE = -1

/**
 * 还原 `switch (string)`。
 *
 * **算法意图**：Java 的 `switch (str)` 在字节码里会先对字符串求 `hashCode()`，
 * 用一个整数 switch 分发到各 hash 桶，桶内再用 `str.equals(...)` 二次比较，
 * 比较成功后再赋一个数值、进入真正的整数 switch。d8/r8 有时还会把第一个 switch
 * 改写成 if 链。本访问器把这种“hash switch + equals 链 + 数值 switch”三件套
 * 合并回一个以字符串为 key 的 switch。
 *
 * Kotlin 转换说明：
 * - `key != DEFAULT_CASE_KEY`、`code == caseData.getCode()` 等对象比较一律改为 `===`；
 * - `CaseInfo.keys` 在 Kotlin 中声明为只读 `List`，原地修改通过 [mutableKeys] 向下转型；
 * - 原 Java 的 `StackOverflowError | Exception` 拆成两个 catch。
 */
@KadxVisitor(
	name = "SwitchOverStringVisitor",
	desc = "Restore switch over string",
	runAfter = [IfRegionVisitor::class],
	runBefore = [ReturnVisitor::class],
)
class SwitchOverStringVisitor :
	AbstractVisitor(),
	IRegionIterativeVisitor {

	override fun visit(mth: MethodNode) {
		if (!CodeFeaturesAttr.contains(mth, CodeFeature.SWITCH)) {
			return
		}
		DepthRegionTraversal.traverseIterative(mth, this)
	}

	override fun visitRegion(mth: MethodNode, region: IRegion): Boolean {
		if (region is SwitchRegion || region is IfRegion) {
			return restoreSwitchOverString(mth, region)
		}
		return false
	}

	private fun restoreSwitchOverString(mth: MethodNode, part1Region: IRegion): Boolean {
		try {
			val strHashInsn = BlockUtils.getLastInsn(RegionUtils.getFirstBlockNode(part1Region))
			val hashcodeInv = if (strHashInsn == null) null else getStrHashcodeInvokeInsn(strHashInsn.getArg(0))
			val strArg = hashcodeInv?.getInstanceArg()
			if (strArg == null || !strArg.isRegister) {
				return false
			}

			val data = SwitchData(mth, part1Region)
			data.setHashcodeInvokeInsn(hashcodeInv)
			data.setStrArg(strArg as RegisterArg)

			val nextContainer = RegionUtils.getNextContainer(mth, part1Region)
			val isPart1Switch = part1Region is SwitchRegion
			val isPart2Switch = nextContainer is SwitchRegion
			if (isPart2Switch) {
				val part2Region = nextContainer as SwitchRegion
				val part2SwInsn = BlockUtils.getLastInsnWithType(part2Region.header, InsnType.SWITCH)
				if (part2SwInsn == null || !part2SwInsn.getArg(0).isRegister) {
					return false
				}
				data.setType(if (isPart1Switch) SwitchStringType.SWITCH_SWITCH else SwitchStringType.IF_SWITCH)
				data.setPart2Region(part2Region)
				data.setNumArg(part2SwInsn.getArg(0) as RegisterArg)
			} else if (isPart1Switch) {
				data.setType(SwitchStringType.SINGLE_SWITCH)
			} else {
				return false
			}

			if (!collectPart1RegionCases(data)) {
				return false
			}
			if (!prepareMergedSwitchCases(data) || !replaceWithMergedSwitch(data)) {
				mth.addWarnComment("Failed to restore switch over string. Please report as a decompilation issue")
				return false
			}
			return true
		} catch (e: StackOverflowError) {
			mth.addWarnComment("Failed to restore switch over string. Please report as a decompilation issue", e)
			return false
		} catch (e: Exception) {
			mth.addWarnComment("Failed to restore switch over string. Please report as a decompilation issue", e)
			return false
		}
	}

	/**
	 * 收集第一段区域（hash switch 或 hash if 链）的 case，并验证：
	 * - case key 确实是字符串哈希值；
	 * - case 块里确实是 `str.equals(...)` 比较；
	 * - 比较成功后给数值变量赋值（SWITCH_SWITCH / IF_SWITCH）。
	 */
	private fun collectPart1RegionCases(data: SwitchData): Boolean {
		// hashcode -> case 块
		val hashCases: MutableMap<Int, BlockNode> = LinkedHashMap()
		if (data.getType() == SwitchStringType.IF_SWITCH) {
			val part1If = data.part1Region as IfRegion
			val part2Header = checkNotNull(data.getPart2Region()).header
			var strHashArg: RegisterArg? = null
			val ifStartBlock = part1If.conditionBlocks[0]
			var hashCmpBlock: BlockNode? = getOnlyOneInsnBlock(ifStartBlock)
			do {
				val curBlock = hashCmpBlock ?: return false
				val ifNode = BlockUtils.getLastInsnWithType(curBlock, InsnType.IF) as IfNode?
				if (ifNode == null || (ifNode.getOp() != IfOp.NE && ifNode.getOp() != IfOp.EQ)) {
					return false
				}
				val isNE = ifNode.getOp() == IfOp.NE
				val thenBlock = getOnlyOneInsnBlock(if (isNE) ifNode.getElseBlock() else ifNode.getThenBlock())
				val elseBlock = getOnlyOneInsnBlock(if (isNE) ifNode.getThenBlock() else ifNode.getElseBlock())
				if (thenBlock == null || elseBlock == null ||
					!ifNode.getArg(0).isRegister || !ifNode.getArg(1).isLiteral
				) {
					return false
				}
				val tmpStrHashArg = ifNode.getArg(0) as RegisterArg
				val literalArg = ifNode.getArg(1) as LiteralArg
				if (strHashArg == null) {
					strHashArg = tmpStrHashArg
				} else if (!strHashArg.sameCodeVar(tmpStrHashArg)) {
					return false
				}
				hashCases[literalArg.literal.toInt()] = thenBlock
				hashCmpBlock = elseBlock
				// part1If 结束：没有更多 hash 比较，下一个块就是 part2 switch
				val fallbackNum = extractConstNumber(data, BlockUtils.getLastInsn(elseBlock))
				val nextBlock = BlockUtils.getNextBlock(elseBlock)
				if (elseBlock === part2Header || (DEFAULT_NUM_VALUE == fallbackNum && nextBlock === part2Header)) {
					break
				}
			} while (true)
		} else {
			val part1Switch = data.part1Region as SwitchRegion
			val swInsn = BlockUtils.getLastInsnWithType(part1Switch.header, InsnType.SWITCH) as SwitchInsn
			checkNotNull(swInsn)
			val keys = swInsn.getKeys()
			val targetBlocks = checkNotNull(swInsn.getTargetBlocks())
			for (i in 0 until keys.size) {
				val caseBlock = getOnlyOneInsnBlock(targetBlocks[i]) ?: return false
				hashCases[keys[i]] = caseBlock
			}
		}

		// 保存到 switchData
		data.setCases(ArrayList(hashCases.size))
		for (hashcode in hashCases.keys) {
			val caseBlock = checkNotNull(hashCases[hashcode])
			var ifStrEqualsInsn = BlockUtils.getLastInsnWithType(caseBlock, InsnType.IF) as IfNode?
			if (!isIfStringEqualsInsn(ifStrEqualsInsn)) {
				return false
			}
			do {
				val strEqualsInsn = checkNotNull(InsnUtils.getWrappedInsn(checkNotNull(ifStrEqualsInsn).getArg(0)))
				val strArg = strEqualsInsn.getArg(0)
				val valArg = strEqualsInsn.getArg(1)
				val strValue = InsnUtils.getConstValueByArg(data.mth.root(), valArg)
				if (data.getStrArg() != strArg || strValue !is String || strValue.hashCode() != hashcode) {
					return false
				}
				val isCmpNE = (ifStrEqualsInsn.getOp() == IfOp.EQ) != ifStrEqualsInsn.getArg(1).isTrue()
				val thenBlock = if (isCmpNE) ifStrEqualsInsn.getElseBlock() else ifStrEqualsInsn.getThenBlock()
				val elseBlock = if (isCmpNE) ifStrEqualsInsn.getThenBlock() else ifStrEqualsInsn.getElseBlock()
				var numValue: Int? = null
				if (data.getType() == SwitchStringType.SWITCH_SWITCH || data.getType() == SwitchStringType.IF_SWITCH) {
					val numInsn = BlockUtils.getLastInsn(getOnlyOneInsnBlock(thenBlock))
					val numArg = checkNotNull(data.getNumArg())
					if (thenBlock != null && (numInsn == null || numInsn.type == InsnType.SWITCH)) {
						// 数值在第一个区域之前赋值，找最近的赋值
						var iDom: BlockNode? = thenBlock.idom
						while (iDom != null && numValue == null) {
							for (insn in iDom.instructions) {
								numValue = extractConstNumber(data, insn)
							}
							iDom = iDom.idom
						}
						if (numValue == null) {
							return false
						}
					} else if (numInsn != null && numArg.sameCodeVar(checkNotNull(numInsn.result))) {
						numValue = extractConstNumber(data, numInsn)
					} else {
						return false
					}
				}
				// 保存字符串与数值赋值
				data.getCases().add(CaseData(strValue, numValue, thenBlock))
				// 可能存在更多字符串比较（同一 hashcode）
				val nextIfBlock = getOnlyOneInsnBlock(elseBlock)
				ifStrEqualsInsn = if (nextIfBlock == null) null else BlockUtils.getLastInsnWithType(nextIfBlock, InsnType.IF) as IfNode?
			} while (isIfStringEqualsInsn(ifStrEqualsInsn))
		}
		return true
	}

	/**
	 * 根据 part2 区域（SINGLE_SWITCH 时就是 part1 区域）构造合并后的 case，
	 * 把 key 替换成字符串。
	 */
	private fun prepareMergedSwitchCases(data: SwitchData): Boolean {
		val part2Region = data.getPart2Region()
		val cases = data.getCases()
		val newCases = ArrayList<SwitchRegion.CaseInfo>()
		data.setNewCases(newCases)
		if (data.getType() == SwitchStringType.SWITCH_SWITCH || data.getType() == SwitchStringType.IF_SWITCH) {
			// 按数值分组
			val casesMap: MutableMap<Int?, MutableList<Any>> = HashMap(cases.size)
			for (caseData in cases) {
				casesMap.computeIfAbsent(caseData.codeNum) { ArrayList() }.add(caseData.strValue)
			}
			for (caseInfo in checkNotNull(part2Region).cases) {
				val newCase = SwitchRegion.CaseInfo(ArrayList(), caseInfo.container)
				for (key in caseInfo.keys) {
					val intKey = unwrapIntKey(key)
					if (key !== SwitchRegion.DEFAULT_CASE_KEY) {
						val strings = casesMap.remove(checkNotNull(intKey))
						if (strings == null || strings.isEmpty()) {
							return false
						}
						mutableKeys(newCase).addAll(strings)
					} else {
						// 最后一个 case：追加所有剩余字符串
						for (strings in casesMap.values) {
							mutableKeys(newCase).addAll(strings)
						}
						casesMap.clear()
						mutableKeys(newCase).add(SwitchRegion.DEFAULT_CASE_KEY)
					}
				}
				newCases.add(newCase)
			}
			if (casesMap.isNotEmpty()) {
				data.mth.addWarnComment("switch over string: strings are not added: " + casesMap.values)
			}
		} else if (data.getType() == SwitchStringType.SINGLE_SWITCH) {
			val part1Region = data.part1Region as SwitchRegion
			val swInsn = BlockUtils.getLastInsnWithType(part1Region.header, InsnType.SWITCH) as SwitchInsn
			val defBlock = checkNotNull(swInsn).getDefTargetBlock()
			if (defBlock != null) {
				cases.add(CaseData(SwitchRegion.DEFAULT_CASE_KEY, DEFAULT_NUM_VALUE, defBlock))
			}
			var lastCaseData: CaseData? = null
			for (caseData in cases) {
				if (lastCaseData != null && lastCaseData.code === caseData.code) {
					// 合并代码块相同的 case
					val lastInfo = checkNotNull(ListUtils.last(newCases))
					mutableKeys(lastInfo).add(caseData.strValue)
				} else {
					val container = RegionUtils.getBlockContainer(part1Region, checkNotNull(caseData.code)) ?: return false
					val newInfo = SwitchRegion.CaseInfo(ArrayList(), container)
					mutableKeys(newInfo).add(caseData.strValue)
					newCases.add(newInfo)
				}
				lastCaseData = caseData
			}
		}
		return true
	}

	/** 用合并后的字符串 switch 替换原代码，并清理旧指令 */
	private fun replaceWithMergedSwitch(data: SwitchData): Boolean {
		val mth = data.mth
		val part1Region = data.part1Region
		val part1Parent = checkNotNull(part1Region.parent)
		val part2Region = data.getPart2Region()
		val keptInsns = ArrayList<InsnNode>()
		val newHeader: BlockNode
		if (data.getType() == SwitchStringType.SWITCH_SWITCH || data.getType() == SwitchStringType.SINGLE_SWITCH) {
			newHeader = (part1Region as SwitchRegion).header
		} else {
			newHeader = checkNotNull(part2Region).header
		}
		// 直接在 switch 中使用字符串参数
		val swInsn = BlockUtils.getLastInsnWithType(newHeader, InsnType.SWITCH)
		val newSwInsn = checkNotNull(swInsn).copyWithoutResult<InsnNode>()
		newSwInsn.replaceArg(checkNotNull(swInsn).getArg(0), data.getStrArg().duplicate())
		BlockUtils.replaceInsn(mth, newHeader, checkNotNull(swInsn), newSwInsn)
		keptInsns.add(newSwInsn)

		val replaceRegion = SwitchRegion(part1Parent, newHeader)
		for (caseInfo in data.getNewCases()) {
			val container = caseInfo.container
			RegionUtils.visitBlocks(mth, container) { b -> keptInsns.addAll(b.instructions) }
			replaceRegion.addCase(Collections.unmodifiableList(caseInfo.keys), container)
			replaceRegion.updateParent(container, replaceRegion)
		}
		if (!part1Parent.replaceSubBlock(part1Region, replaceRegion)) {
			return false
		}

		// 删除旧代码
		try {
			val removeInsns = ArrayList(RegionUtils.collectInsns(mth, part1Region))
			if (part2Region != null) {
				removeInsns.addAll(RegionUtils.collectInsns(mth, part2Region))
				@Suppress("UNCHECKED_CAST")
				(checkNotNull(part2Region.parent).subBlocks as MutableList<IContainer>).remove(part2Region)
			}
			removeInsns.removeAll(keptInsns)
			for (insn in removeInsns) {
				insn.add(AFlag.REMOVE)
			}
			// 数值可能在第一个区域之前被赋值
			val numArg = data.getNumArg()
			if (numArg != null) {
				for (ssaVar in checkNotNull(numArg.sVar).codeVar.ssaVars) {
					val assignInsn = ssaVar.assignInsn
					if (assignInsn != null) {
						assignInsn.add(AFlag.REMOVE)
					}
					for (useArg in ssaVar.useList) {
						val parentInsn = useArg.getParentInsn()
						if (parentInsn != null) {
							parentInsn.add(AFlag.REMOVE)
						}
					}
					mth.removeSVar(ssaVar)
				}
			}
			InsnRemover.removeAllMarked(mth)
			InsnRemover.remove(mth, data.getHashcodeInvokeInsn())
		} catch (e: StackOverflowError) {
			mth.addWarnComment("Failed to clean up code after switch over string restore", e)
		} catch (e: Exception) {
			mth.addWarnComment("Failed to clean up code after switch over string restore", e)
		}
		return true
	}

	/** CaseInfo.keys 实际都是可变列表，这里统一向下转型以便原地追加 */
	@Suppress("UNCHECKED_CAST")
	private fun mutableKeys(caseInfo: SwitchRegion.CaseInfo): MutableList<Any> = caseInfo.keys as MutableList<Any>

	private fun extractConstNumber(switchData: SwitchData, numInsn: InsnNode?): Int? {
		if (numInsn == null || numInsn.argsCount != 1) {
			return null
		}
		val constVal = InsnUtils.getConstValueByArg(switchData.mth.root(), numInsn.getArg(0))
		if (constVal is LiteralArg) {
			val numArg = switchData.getNumArg()
			if (numArg != null && numArg.sameCodeVar(checkNotNull(numInsn.result))) {
				return constVal.literal.toInt()
			}
		}
		return null
	}

	private fun unwrapIntKey(key: Any): Int? {
		if (key is Int) {
			return key
		}
		if (key is FieldNode) {
			val encodedValue = key.get(KadxAttrType.CONSTANT_VALUE)
			if (encodedValue != null && encodedValue.type == EncodedType.ENCODED_INT) {
				return encodedValue.value as Int
			}
			return null
		}
		return null
	}

	private fun getStrHashcodeInvokeInsn(arg: InsnArg): InvokeNode? {
		var insn: InsnNode? = null
		if (arg.isRegister) {
			insn = (arg as RegisterArg).assignInsn
		} else if (arg.isInsnWrap) {
			insn = (arg as InsnWrapArg).wrapInsn
		}
		if (insn != null && insn.type == InsnType.INVOKE) {
			val invInsn = insn as InvokeNode
			if (invInsn.callMth.rawFullId == "java.lang.String.hashCode()I") {
				return invInsn
			}
		}
		return null
	}

	private fun isIfStringEqualsInsn(ifInsn: InsnNode?): Boolean {
		if (ifInsn != null && ifInsn.type == InsnType.IF && ifInsn.argsCount == 2) {
			val wrapped = InsnUtils.getWrappedInsn(ifInsn.getArg(0))
			return wrapped != null && wrapped.type == InsnType.INVOKE &&
				(wrapped as InvokeNode).callMth.rawFullId == "java.lang.String.equals(Ljava/lang/Object;)Z"
		}
		return false
	}

	private fun getOnlyOneInsnBlock(b0: BlockNode?): BlockNode? {
		var b = b0
		while (b != null) {
			val size = b.instructions.size
			if (size == 0) {
				b = BlockUtils.getNextBlock(b)
				continue
			}
			return if (size == 1) b else null
		}
		return null
	}

	/** switch 还原过程中的临时数据载体 */
	private class SwitchData(val mth: MethodNode, val part1Region: IRegion) {
		private var type: SwitchStringType = SwitchStringType.SWITCH_SWITCH
		private var part2Region: SwitchRegion? = null
		private var cases: MutableList<CaseData>? = null
		private var newCases: MutableList<SwitchRegion.CaseInfo>? = null
		private var numArg: RegisterArg? = null
		private var strArg: RegisterArg? = null
		private var hashcodeInvokeInsn: InsnNode? = null

		fun getType(): SwitchStringType = type

		fun setType(type: SwitchStringType) {
			this.type = type
		}

		fun getCases(): MutableList<CaseData> = checkNotNull(cases)

		fun setCases(cases: MutableList<CaseData>) {
			this.cases = cases
		}

		fun getNewCases(): MutableList<SwitchRegion.CaseInfo> = checkNotNull(newCases)

		fun setNewCases(cases: MutableList<SwitchRegion.CaseInfo>) {
			this.newCases = cases
		}

		fun getPart2Region(): SwitchRegion? = part2Region

		fun setPart2Region(part2Region: SwitchRegion?) {
			this.part2Region = part2Region
		}

		fun getNumArg(): RegisterArg? = numArg

		fun setNumArg(numArg: RegisterArg) {
			this.numArg = numArg
		}

		fun getStrArg(): RegisterArg = checkNotNull(strArg)

		fun setStrArg(strArg: RegisterArg) {
			this.strArg = strArg
		}

		fun getHashcodeInvokeInsn(): InsnNode? = hashcodeInvokeInsn

		fun setHashcodeInvokeInsn(hashcodeInvokeInsn: InsnNode) {
			this.hashcodeInvokeInsn = hashcodeInvokeInsn
		}
	}

	/** 单个字符串 case：字符串值 + 对应的数值 / 代码块 */
	private class CaseData(val strValue: Any, val codeNum: Int?, val code: BlockNode?) {

		override fun toString(): String = "CaseData{$strValue}"
	}

	private enum class SwitchStringType {
		SINGLE_SWITCH,
		IF_SWITCH,
		SWITCH_SWITCH,
	}
}
