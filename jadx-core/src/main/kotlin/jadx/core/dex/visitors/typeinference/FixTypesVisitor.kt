package jadx.core.dex.visitors.typeinference

import jadx.core.Consts
import jadx.core.clsp.ClspGraph
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.PhiListAttr
import jadx.core.dex.instructions.ArithNode
import jadx.core.dex.instructions.ArithOp
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.PhiInsn
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.args.SSAVar
import jadx.core.dex.instructions.mods.TernaryInsn
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.AbstractVisitor
import jadx.core.dex.visitors.InitCodeVariables
import jadx.core.dex.visitors.JadxVisitor
import jadx.core.dex.visitors.ModVisitor
import jadx.core.dex.visitors.blocks.BlockSplitter
import jadx.core.utils.BlockUtils
import jadx.core.utils.InsnList
import jadx.core.utils.InsnUtils
import jadx.core.utils.ListUtils
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxOverflowException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashSet

/**
 * 类型修正访问器：当类型推导无法直接得到确定类型时，尝试一系列“补救手段”。
 *
 * **算法意图**：类型推导（[TypeInferenceVisitor]）失败后，本 Pass 按顺序尝试：
 * 1. [applyFieldType]：用字段声明类型 + 在使用点插入显式 cast；
 * 2. [tryRestoreTypeVarCasts]：把 `(Comparable)` 形式的 cast 还原成泛型变量 cast；
 * 3. [tryInsertCasts]：为泛型变量插入 cast 指令；
 * 4. [tryDeduceTypes]：从边界、可能类型、父类型中挑选；
 * 5. [trySplitConstInsns]：复制常量指令，避免多个使用点互相约束；
 * 6. [tryToFixIncompatiblePrimitives]：修正 boolean 与整型不兼容的用法；
 * 7. [tryToForceImmutableTypes]：对不可变类型强制取已知类型；
 * 8. [tryInsertAdditionalMove]：在 PHI 前插入 MOVE 指令建立“软”类型链接；
 * 9. [runMultiVariableSearch]：多变量联合搜索；
 * 10. [tryRemoveGenerics]：最后手段，退化为原始类型。
 *
 * 每个手段成功后都会重新检查类型是否全部已知，全部已知即提前结束。
 *
 * **Kotlin 转换说明**：热点遍历保持普通 `for` 循环；`==` 对象比较改为 `===`；
 * 局部变量 `var`（Java 变量名）重命名为 `ssaVar`（Kotlin 中 `var` 是关键字）。
 */
@JadxVisitor(
	name = "Fix Types Visitor",
	desc = "Try various methods to fix unresolved types",
	runAfter = [TypeInferenceVisitor::class],
	runBefore = [FinishTypeInference::class],
)
class FixTypesVisitor : AbstractVisitor() {

	private val typeInference = TypeInferenceVisitor()

	private lateinit var typeUpdate: TypeUpdate
	private lateinit var resolvers: List<(MethodNode) -> Boolean>

	override fun init(root: RootNode) {
		typeUpdate = root.typeUpdate
		typeInference.init(root)
		resolvers = listOf(
			this::applyFieldType,
			this::tryRestoreTypeVarCasts,
			this::tryInsertCasts,
			this::tryDeduceTypes,
			this::trySplitConstInsns,
			this::tryToFixIncompatiblePrimitives,
			this::tryToForceImmutableTypes,
			this::tryInsertAdditionalMove,
			this::runMultiVariableSearch,
			this::tryRemoveGenerics,
		)
	}

	override fun visit(mth: MethodNode) {
		if (mth.isNoCode() || checkTypes(mth)) {
			return
		}
		try {
			for (resolver in resolvers) {
				if (resolver(mth) && checkTypes(mth)) {
					break
				}
			}
		} catch (e: Exception) {
			mth.addError("Types fix failed", e)
		}
	}

	/** 检查方法内所有 SSA 变量的类型是否都已确定。 */
	private fun checkTypes(mth: MethodNode): Boolean {
		for (ssaVar in mth.SVars) {
			val type = ssaVar.typeInfo.getType()
			if (!type.isTypeKnown()) {
				return false
			}
		}
		return true
	}

	private fun runMultiVariableSearch(mth: MethodNode): Boolean {
		try {
			val typeSearch = TypeSearch(mth)
			if (!typeSearch.run()) {
				mth.addWarnComment("Multi-variable type inference failed")
			}
			for (ssaVar in mth.SVars) {
				if (!ssaVar.typeInfo.getType().isTypeKnown()) {
					return false
				}
			}
			return true
		} catch (e: Exception) {
			mth.addWarnComment("Multi-variable type inference failed. Error: " + Utils.getStackTrace(e))
			return false
		}
	}

	private fun setBestType(mth: MethodNode, ssaVar: SSAVar): Boolean {
		try {
			return calculateFromBounds(mth, ssaVar)
		} catch (e: JadxOverflowException) {
			throw e
		} catch (e: Exception) {
			mth.addWarnComment("Failed to calculate best type for var: " + ssaVar, e)
			return false
		}
	}

	private fun calculateFromBounds(mth: MethodNode, ssaVar: SSAVar): Boolean {
		val typeInfo = ssaVar.typeInfo
		val bounds = typeInfo.bounds
		val bestTypeOpt = selectBestTypeFromBounds(bounds)
		if (bestTypeOpt == null) {
			if (Consts.DEBUG_TYPE_INFERENCE) {
				LOG.warn("Failed to select best type from bounds, count={} : ", bounds.size)
				for (bound in bounds) {
					LOG.warn("  {}", bound)
				}
			}
			return false
		}
		val candidateType = bestTypeOpt
		val result = typeUpdate.apply(mth, ssaVar, candidateType)
		if (result == TypeUpdateResult.REJECT) {
			if (Consts.DEBUG_TYPE_INFERENCE) {
				if (ssaVar.typeInfo.getType() == candidateType) {
					LOG.info("Same type rejected: {} -> {}, bounds: {}", ssaVar, candidateType, bounds)
				} else if (candidateType.isTypeKnown()) {
					LOG.debug("Type rejected: {} -> {}, bounds: {}", ssaVar, candidateType, bounds)
				}
			}
			return false
		}
		return result == TypeUpdateResult.CHANGED
	}

	/**
	 * 从所有边界里选出“最窄”的类型。
	 * 原 Java 用 Stream.max(comparator) 实现，这里改写为等价的普通循环（热点路径）。
	 */
	private fun selectBestTypeFromBounds(bounds: Set<ITypeBound>): ArgType? {
		var best: ArgType? = null
		val comparator = typeUpdate.typeCompare.comparator
		for (bound in bounds) {
			val type: ArgType? = bound.type
			if (type != null && (best == null || comparator.compare(best, type) < 0)) {
				best = type
			}
		}
		return best
	}

	private fun tryPossibleTypes(mth: MethodNode, ssaVar: SSAVar, type: ArgType): Boolean {
		val types = makePossibleTypesList(type, ssaVar)
		if (types.isEmpty()) {
			return false
		}
		for (candidateType in types) {
			val result = typeUpdate.apply(mth, ssaVar, candidateType)
			if (result == TypeUpdateResult.CHANGED) {
				return true
			}
		}
		return false
	}

	private fun makePossibleTypesList(type: ArgType, ssaVar: SSAVar?): List<ArgType> {
		if (type.isArray()) {
			val list = ArrayList<ArgType>()
			for (arrElemType in makePossibleTypesList(checkNotNull(type.getArrayElement()), null)) {
				list.add(ArgType.array(arrElemType))
			}
			return list
		}
		if (ssaVar != null) {
			for (b in ssaVar.typeInfo.bounds) {
				val boundType = b.type
				if (boundType.isObject() || boundType.isArray()) {
					// 已有对象/数组边界，不再尝试基本类型
					return Collections.emptyList()
				}
			}
		}
		val list = ArrayList<ArgType>()
		for (possibleType in type.getPossibleTypes()) {
			if (possibleType == PrimitiveType.VOID) {
				continue
			}
			list.add(ArgType.convertFromPrimitiveType(possibleType))
		}
		return list
	}

	private fun tryDeduceTypes(mth: MethodNode): Boolean {
		var fixed = false
		for (ssaVar in mth.SVars) {
			if (deduceType(mth, ssaVar)) {
				fixed = true
			}
		}
		return fixed
	}

	private fun deduceType(mth: MethodNode, ssaVar: SSAVar): Boolean {
		if (ssaVar.isTypeImmutable()) {
			return false
		}
		val type = ssaVar.typeInfo.getType()
		if (type.isTypeKnown()) {
			return false
		}
		// 再次尝试从边界选最优类型
		if (setBestType(mth, ssaVar)) {
			return true
		}
		// 尝试所有可能类型（对基本类型有用）
		if (tryPossibleTypes(mth, ssaVar, type)) {
			return true
		}
		// 对象类型尝试父类型
		if (tryWiderObjects(mth, ssaVar)) {
			return true
		}
		return false
	}

	private fun tryRemoveGenerics(mth: MethodNode): Boolean {
		var resolved = true
		for (ssaVar in mth.SVars) {
			val type = ssaVar.typeInfo.getType()
			if (!type.isTypeKnown() && !ssaVar.isTypeImmutable() && !tryRawType(mth, ssaVar)) {
				resolved = false
			}
		}
		return resolved
	}

	private fun tryRawType(mth: MethodNode, ssaVar: SSAVar): Boolean {
		val objTypes = LinkedHashSet<ArgType>()
		for (bound in ssaVar.typeInfo.bounds) {
			val boundType = bound.type
			if (boundType.isTypeKnown() && boundType.isObject()) {
				objTypes.add(boundType)
			}
		}
		if (objTypes.isEmpty()) {
			return false
		}
		for (objType in objTypes) {
			if (checkRawType(mth, ssaVar, objType)) {
				mth.addDebugComment(
					"Type inference failed for " + ssaVar.toShortString() +
						". Raw type applied. Possible types: " + Utils.listToString(objTypes),
				)
				return true
			}
		}
		return false
	}

	private fun checkRawType(mth: MethodNode, ssaVar: SSAVar, objType: ArgType): Boolean {
		if (objType.isObject() && objType.containsGeneric()) {
			val rawType = if (objType.isGenericType()) ArgType.OBJECT else ArgType.`object`(objType.getObject())
			val result = typeUpdate.applyWithWiderAllow(mth, ssaVar, rawType)
			return result == TypeUpdateResult.CHANGED
		}
		return false
	}

	/**
	 * 用字段（IGET/SGET）的声明类型作为变量类型，并在使用点插入显式 cast。
	 */
	private fun applyFieldType(mth: MethodNode): Boolean {
		try {
			var changed = false
			// 会新增 SSA 变量，因此不能使用 for-each
			val sVars = mth.SVars
			val varsCount = sVars.size
			for (i in 0 until varsCount) {
				val ssaVar = sVars[i]
				if (tryFieldTypeWithNewCasts(mth, ssaVar, true)) {
					changed = true
				}
			}
			if (!changed) {
				return false
			}
			// 重新跑一遍完整类型推导
			InitCodeVariables.rerun(mth)
			typeInference.initTypeBounds(mth)
			typeInference.runTypePropagation(mth)

			// 检查发生变化的变量类型是否已经固定
			var success = true
			for (ssaVar in mth.SVars) {
				if (tryFieldTypeWithNewCasts(mth, ssaVar, false)) {
					success = false
				}
			}
			if (!success) {
				typeInference.initTypeBounds(mth)
				typeInference.runTypePropagation(mth)
				mth.addWarnComment("Type inference incomplete: some casts might be missing")
			}
			return success
		} catch (e: Exception) {
			mth.addWarnComment("Type inference fix 'apply assigned field type' failed", e)
			return false
		}
	}

	private fun tryFieldTypeWithNewCasts(mth: MethodNode, ssaVar: SSAVar, insertCasts: Boolean): Boolean {
		val type = ssaVar.typeInfo.getType()
		if (type.isTypeKnown() || ssaVar.isTypeImmutable()) {
			return false
		}
		val assignInsn = ssaVar.assignInsn ?: return false
		val insnType = assignInsn.type
		if (insnType != InsnType.IGET && insnType != InsnType.SGET) {
			return false
		}
		val fieldType = checkNotNull(assignInsn.getResult()).getInitType()
		// 应该使用字段类型
		if (insertCasts) {
			// 尝试找到使用点并插入 cast
			var inserted = false
			for (useArg in ssaVar.useList) {
				if (insertExplicitUseCast(mth, ssaVar, useArg, fieldType)) {
					inserted = true
				}
			}
			return inserted
		}
		// 强制使用字段类型，会让类型推导不完整，但总好过完全未知
		ssaVar.setType(fieldType)
		return true
	}

	private fun insertExplicitUseCast(mth: MethodNode, ssaVar: SSAVar, useArg: RegisterArg, fieldType: ArgType): Boolean {
		val parentInsn = useArg.getParentInsn()
		if (!InsnUtils.isInsnType(parentInsn, InsnType.INVOKE)) {
			return false
		}
		val invoke = parentInsn as InvokeNode
		val instanceArg = invoke.getInstanceArg()
		if (instanceArg == null || !instanceArg.isSameVar(ssaVar)) {
			return false
		}
		val details = mth.root().getMethodUtils().getMethodDetails(invoke) ?: return false
		var newCasts = 0
		var k = -1
		for (invArg in invoke.argList) {
			if (invArg === instanceArg) {
				continue
			}
			k++
			if (!invArg.isRegister) {
				continue
			}
			val detailsArg = details.getArgTypes()[k]
			val invArgType = invArg.getType()
			val resolvedType = mth.root().getTypeUtils().replaceClassGenerics(fieldType, invArgType, detailsArg)
			if (resolvedType != null && resolvedType != invArgType) {
				val castInsn = insertUseCast(mth, invArg as RegisterArg, resolvedType)
				if (castInsn != null) {
					castInsn.add(AFlag.EXPLICIT_CAST)
					newCasts++
				}
			}
		}
		return newCasts > 0
	}

	/**
	 * 把 check cast 还原为泛型变量扩展类型的 cast：
	 *
	 * `<T extends Comparable> T var = (Comparable) obj;` 变为 `T var = (T) obj;`。
	 */
	private fun tryRestoreTypeVarCasts(mth: MethodNode): Boolean {
		var changed = 0
		val mthSVars = mth.SVars
		for (ssaVar in mthSVars) {
			changed += restoreTypeVarCasts(ssaVar)
		}
		if (changed == 0) {
			return false
		}
		if (Consts.DEBUG_TYPE_INFERENCE) {
			mth.addDebugComment("Restore $changed type vars casts")
		}
		typeInference.initTypeBounds(mth)
		return typeInference.runTypePropagation(mth)
	}

	private fun restoreTypeVarCasts(ssaVar: SSAVar): Int {
		val typeInfo = ssaVar.typeInfo
		val bounds = typeInfo.bounds
		if (!ListUtils.anyMatch(bounds) { it.type.isGenericType() }) {
			return 0
		}
		val casts = ListUtils.filter(bounds) { it is TypeBoundCheckCastAssign }
		if (casts.isEmpty()) {
			return 0
		}
		val bestType = selectBestTypeFromBounds(bounds) ?: ArgType.UNKNOWN
		if (!bestType.isGenericType()) {
			return 0
		}
		val extendTypes = bestType.getExtendTypes()
		if (extendTypes.size != 1) {
			return 0
		}
		var fixed = 0
		val extendType = extendTypes[0]
		for (bound in casts) {
			val cast = bound as TypeBoundCheckCastAssign
			val castType = cast.type
			val result = typeUpdate.typeCompare.compareTypes(extendType, castType)
			if (result.isEqual() || result == TypeCompareEnum.NARROW_BY_GENERIC) {
				cast.insn.index = bestType
				fixed++
			}
		}
		return fixed
	}

	private fun tryInsertCasts(mth: MethodNode): Boolean {
		var added = 0
		val mthSVars = mth.SVars
		val varsCount = mthSVars.size
		for (i in 0 until varsCount) {
			val ssaVar = mthSVars[i]
			val type = ssaVar.typeInfo.getType()
			if (!type.isTypeKnown() && !ssaVar.isTypeImmutable()) {
				added += tryInsertVarCast(mth, ssaVar)
			}
		}
		if (added != 0) {
			InitCodeVariables.rerun(mth)
			typeInference.initTypeBounds(mth)
			return typeInference.runTypePropagation(mth)
		}
		return false
	}

	private fun tryInsertVarCast(mth: MethodNode, ssaVar: SSAVar): Int {
		for (bound in ssaVar.typeInfo.bounds) {
			val boundType = bound.type
			if (boundType.isTypeKnown() &&
				boundType != ssaVar.typeInfo.getType() &&
				boundType.containsTypeVariable() &&
				!mth.root().getTypeUtils().containsUnknownTypeVar(mth, boundType)
			) {
				val castInsn = insertAssignCast(mth, ssaVar, boundType)
				if (castInsn != null) {
					castInsn.add(AFlag.SOFT_CAST)
					return 1
				}
				return insertUseCasts(mth, ssaVar)
			}
		}
		return 0
	}

	private fun insertUseCasts(mth: MethodNode, ssaVar: SSAVar): Int {
		val useList = ssaVar.useList
		if (useList.isEmpty()) {
			return 0
		}
		var useCasts = 0
		for (useReg in ArrayList(useList)) {
			val castInsn = insertUseCast(mth, useReg, useReg.getInitType())
			if (castInsn != null) {
				castInsn.add(AFlag.SOFT_CAST)
				useCasts++
			}
		}
		return useCasts
	}

	private fun insertAssignCast(mth: MethodNode, ssaVar: SSAVar, castType: ArgType): IndexInsnNode? {
		val assignArg = ssaVar.assign
		val assignInsn = assignArg.getParentInsn()
		if (assignInsn == null || assignInsn.type == InsnType.PHI) {
			return null
		}
		val assignBlock = BlockUtils.getBlockByInsn(mth, assignInsn) ?: return null
		assignInsn.setResult(assignArg.duplicateWithNewSSAVar(mth))
		val castInsn = makeCastInsn(assignArg.duplicate(), checkNotNull(assignInsn.getResult()).duplicate(), castType)
		if (!BlockUtils.insertAfterInsn(assignBlock, assignInsn, castInsn)) {
			return null
		}
		return castInsn
	}

	private fun insertUseCast(mth: MethodNode, useArg: RegisterArg, castType: ArgType): IndexInsnNode? {
		val useInsn = useArg.getParentInsn()
		if (useInsn == null || useInsn.type == InsnType.PHI) {
			return null
		}
		if (useInsn.type == InsnType.IF && useInsn.getArg(1).isZeroConst()) {
			// 与 null 比较时不需要 cast
			return null
		}
		val useBlock = BlockUtils.getBlockByInsn(mth, useInsn) ?: return null
		val castInsn = makeCastInsn(
			useArg.duplicateWithNewSSAVar(mth),
			useArg.duplicate(),
			castType,
		)
		useInsn.replaceArg(useArg, checkNotNull(castInsn.getResult()).duplicate())
		val inserted = BlockUtils.insertBeforeInsn(useBlock, useInsn, castInsn)
		if (!inserted) {
			return null
		}
		if (Consts.DEBUG_TYPE_INFERENCE) {
			LOG.info("Insert cast for {} before {} in {}", useArg, useInsn, useBlock)
		}
		return castInsn
	}

	private fun makeCastInsn(result: RegisterArg, arg: RegisterArg, castType: ArgType): IndexInsnNode {
		val castInsn = IndexInsnNode(InsnType.CHECK_CAST, castType, 1)
		castInsn.setResult(result)
		castInsn.addArg(arg)
		castInsn.add(AFlag.SYNTHETIC)
		return castInsn
	}

	private fun trySplitConstInsns(mth: MethodNode): Boolean {
		var constSplit = false
		for (ssaVar in ArrayList(mth.SVars)) {
			if (checkAndSplitConstInsn(mth, ssaVar)) {
				constSplit = true
			}
		}
		if (!constSplit) {
			return false
		}
		InitCodeVariables.rerun(mth)
		typeInference.initTypeBounds(mth)
		return typeInference.runTypePropagation(mth)
	}

	private fun checkAndSplitConstInsn(mth: MethodNode, ssaVar: SSAVar): Boolean {
		val type = ssaVar.typeInfo.getType()
		if (type.isTypeKnown() || ssaVar.isTypeImmutable()) {
			return false
		}
		return splitByPhi(mth, ssaVar) || dupConst(mth, ssaVar)
	}

	private fun dupConst(mth: MethodNode, ssaVar: SSAVar): Boolean {
		val assignInsn = ssaVar.assign.assignInsn
		if (assignInsn == null || !InsnUtils.isInsnType(assignInsn, InsnType.CONST)) {
			return false
		}
		if (ssaVar.useList.size < 2) {
			return false
		}
		val assignBlock = BlockUtils.getBlockByInsn(mth, assignInsn) ?: return false
		assignInsn.remove(AFlag.DONT_INLINE)
		val insertIndex = 1 + BlockUtils.getInsnIndexInBlock(assignBlock, assignInsn)
		val useList = ArrayList(ssaVar.useList)
		val useCount = useList.size
		for (i in 0 until useCount) {
			val useArg = useList[i]
			useArg.remove(AFlag.DONT_INLINE_CONST)
			if (i == 0) {
				continue
			}
			val useInsn = useArg.getParentInsn() ?: continue
			val newInsn = assignInsn.copyWithNewSsaVar(mth)
			assignBlock.instructions.add(insertIndex, newInsn)
			useInsn.replaceArg(useArg, checkNotNull(newInsn.getResult()).duplicate())
		}
		if (Consts.DEBUG_TYPE_INFERENCE) {
			LOG.debug("Duplicate const insn {} times: {} in {}", useList.size, assignInsn, assignBlock)
		}
		return true
	}

	/** 为每个 PHI 分别生成一条独立的 CONST 指令。 */
	private fun splitByPhi(mth: MethodNode, ssaVar: SSAVar): Boolean {
		if (ssaVar.usedInPhi.size < 2) {
			return false
		}
		val assignInsn = ssaVar.assign.assignInsn
		val constInsn = InsnUtils.checkInsnType(assignInsn, InsnType.CONST) ?: return false
		val blockNode = BlockUtils.getBlockByInsn(mth, constInsn) ?: return false
		var first = true
		for (phiInsn in ssaVar.usedInPhi) {
			if (first) {
				first = false
				continue
			}
			val copyInsn = constInsn.copyWithNewSsaVar(mth)
			copyInsn.add(AFlag.SYNTHETIC)
			BlockUtils.insertAfterInsn(blockNode, constInsn, copyInsn)

			val phiArg = phiInsn.getArgBySsaVar(ssaVar)
			phiInsn.replaceArg(checkNotNull(phiArg), checkNotNull(copyInsn.getResult()).duplicate())
		}
		return true
	}

	private fun tryInsertAdditionalMove(mth: MethodNode): Boolean {
		var insnsAdded = 0
		for (block in checkNotNull(mth.basicBlocks)) {
			val phiListAttr = block.get(AType.PHI_LIST)
			if (phiListAttr != null) {
				for (phiInsn in phiListAttr.list) {
					insnsAdded += tryInsertAdditionalInsn(mth, phiInsn)
				}
			}
		}
		if (insnsAdded == 0) {
			return false
		}
		if (Consts.DEBUG_TYPE_INFERENCE) {
			mth.addDebugComment("Additional $insnsAdded move instructions added to help type inference")
		}
		InitCodeVariables.rerun(mth)
		typeInference.initTypeBounds(mth)
		if (typeInference.runTypePropagation(mth) && checkTypes(mth)) {
			return true
		}
		return tryDeduceTypes(mth)
	}

	/**
	 * 在 PHI 的汇入块前插入 MOVE 指令，建立“软”类型链接，
	 * 使得被 PHI 合并的块可以使用不同的类型。
	 */
	private fun tryInsertAdditionalInsn(mth: MethodNode, phiInsn: PhiInsn): Int {
		val phiType = getCommonTypeForPhiArgs(phiInsn)
		if (phiType != null && phiType.isTypeKnown()) {
			// 所有参数类型相同且已知，无需处理
			return 0
		}
		// 先检查能否插入
		if (insertMovesForPhi(mth, phiInsn, false) == 0) {
			return 0
		}
		// 检查通过，正式插入
		return insertMovesForPhi(mth, phiInsn, true)
	}

	private fun getCommonTypeForPhiArgs(phiInsn: PhiInsn): ArgType? {
		var phiArgType: ArgType? = null
		for (arg in phiInsn.getArguments()) {
			val type = arg.getType()
			if (phiArgType == null) {
				phiArgType = type
			} else if (phiArgType != type) {
				return null
			}
		}
		return phiArgType
	}

	private fun insertMovesForPhi(mth: MethodNode, phiInsn: PhiInsn, apply: Boolean): Int {
		val argsCount = phiInsn.argsCount
		var count = 0
		for (argIndex in 0 until argsCount) {
			val reg = phiInsn.getArg(argIndex)
			val startBlock = phiInsn.getBlockByArgIndex(argIndex)
			val blockNode = checkBlockForInsnInsert(startBlock)
			if (blockNode == null) {
				mth.addDebugComment("Failed to insert an additional move for type inference into block $startBlock")
				return 0
			}
			var add = true
			val ssaVar = checkNotNull(reg.sVar)
			val assignInsn = ssaVar.assign.assignInsn
			if (assignInsn != null) {
				val assignType = assignInsn.type
				if (assignType == InsnType.CONST ||
					(assignType == InsnType.MOVE && ssaVar.useCount == 1)
				) {
					add = false
				}
			}
			if (add) {
				count++
				if (apply) {
					insertMove(mth, blockNode, phiInsn, reg)
				}
			}
		}
		return count
	}

	private fun insertMove(mth: MethodNode, blockNode: BlockNode, phiInsn: PhiInsn, reg: RegisterArg) {
		val ssaVar = checkNotNull(reg.sVar)
		val regNum = reg.regNum
		val resultArg = reg.duplicate(regNum, null)
		val newSsaVar = mth.makeNewSVar(resultArg)
		val arg = reg.duplicate(regNum, ssaVar)

		val moveInsn = InsnNode(InsnType.MOVE, 1)
		moveInsn.setResult(resultArg)
		moveInsn.addArg(arg)
		moveInsn.add(AFlag.SYNTHETIC)
		blockNode.instructions.add(moveInsn)

		phiInsn.replaceArg(reg, reg.duplicate(regNum, newSsaVar))
	}

	private fun checkBlockForInsnInsert(blockNode: BlockNode): BlockNode? {
		if (blockNode.isSynthetic()) {
			return null
		}
		val lastInsn = BlockUtils.getLastInsn(blockNode)
		if (lastInsn != null && BlockSplitter.isSeparate(lastInsn.type)) {
			// 含“分离”指令的块无法插入 move，沿单前驱路径向前找
			val preds = blockNode.getPredecessors()
			if (preds.size == 1) {
				return checkBlockForInsnInsert(preds[0])
			}
			return null
		}
		return blockNode
	}

	private fun tryWiderObjects(mth: MethodNode, ssaVar: SSAVar): Boolean {
		val objTypes = LinkedHashSet<ArgType>()
		for (bound in ssaVar.typeInfo.bounds) {
			val boundType = bound.type
			if (boundType.isTypeKnown() && boundType.isObject()) {
				objTypes.add(boundType)
			}
		}
		if (objTypes.isEmpty()) {
			return false
		}
		val clsp: ClspGraph = checkNotNull(mth.root().getClsp())
		for (objType in objTypes) {
			for (ancestor in clsp.getSuperTypes(objType.getObject())) {
				val ancestorType = ArgType.`object`(ancestor)
				val result = typeUpdate.applyWithWiderAllow(mth, ssaVar, ancestorType)
				if (result == TypeUpdateResult.CHANGED) {
					return true
				}
			}
		}
		return false
	}

	private fun tryToFixIncompatiblePrimitives(mth: MethodNode): Boolean {
		var fixed = false
		val ssaVars = mth.SVars
		val ssaVarsCount = ssaVars.size
		// 修正后会新增变量到列表末尾，因此不能用 for-each
		for (i in 0 until ssaVarsCount) {
			if (processIncompatiblePrimitives(mth, ssaVars[i])) {
				fixed = true
			}
		}
		if (!fixed) {
			return false
		}
		InitCodeVariables.rerun(mth)
		typeInference.initTypeBounds(mth)
		return typeInference.runTypePropagation(mth)
	}

	private fun processIncompatiblePrimitives(mth: MethodNode, ssaVar: SSAVar): Boolean {
		val typeInfo = ssaVar.typeInfo
		if (typeInfo.getType().isTypeKnown()) {
			return false
		}
		var assigned = false
		for (bound in typeInfo.bounds) {
			val boundType = bound.type
			when (bound.bound) {
				BoundEnum.ASSIGN -> {
					if (!boundType.contains(PrimitiveType.BOOLEAN)) {
						return false
					}
					assigned = true
				}

				BoundEnum.USE -> {
					if (!boundType.canBeAnyNumber()) {
						return false
					}
				}
			}
		}
		if (!assigned) {
			return false
		}

		var fixed = false
		for (arg in ArrayList(ssaVar.useList)) {
			if (fixBooleanUsage(mth, arg)) {
				fixed = true
				if (Consts.DEBUG_TYPE_INFERENCE) {
					LOG.info("Fixed boolean usage for arg {} from {}", arg, arg.getParentInsn())
				}
			}
		}
		return fixed
	}

	private fun fixBooleanUsage(mth: MethodNode, boundArg: RegisterArg): Boolean {
		val boundType = boundArg.getInitType()
		if (boundType == ArgType.BOOLEAN || (boundType.isTypeKnown() && !boundType.isPrimitive())) {
			return false
		}
		val insn = boundArg.getParentInsn() ?: return false
		if (insn.type == InsnType.IF) {
			return false
		}
		val blockNode = BlockUtils.getBlockByInsn(mth, insn) ?: return false
		val insnList = blockNode.instructions
		val insnIndex = InsnList.getIndex(insnList, insn)
		if (insnIndex == -1) {
			return false
		}
		val insnType = insn.type
		if (insnType == InsnType.CAST) {
			// 替换 cast
			val type = (insn as IndexInsnNode).index as ArgType
			val convertInsn = prepareBooleanConvertInsn(checkNotNull(insn.getResult()), boundArg, type)
			BlockUtils.replaceInsn(mth, blockNode, insnIndex, convertInsn)
			return true
		}
		if (insnType == InsnType.ARITH) {
			val arithInsn = insn as ArithNode
			if (arithInsn.op == ArithOp.XOR && arithInsn.argsCount == 2) {
				// 把 (boolean ^ 1) 替换为 (!boolean)
				val secondArg = arithInsn.getArg(1)
				if (secondArg.isLiteral && (secondArg as LiteralArg).literal == 1L) {
					val convertInsn = notBooleanToInt(arithInsn, boundArg)
					BlockUtils.replaceInsn(mth, blockNode, insnIndex, convertInsn)
					return true
				}
			}
		}

		// 在指令前插入
		val resultArg = boundArg.duplicateWithNewSSAVar(mth)
		val convertInsn = prepareBooleanConvertInsn(resultArg, boundArg, boundType)
		insnList.add(insnIndex, convertInsn)
		insn.replaceArg(boundArg, checkNotNull(convertInsn.getResult()).duplicate())
		return true
	}

	private fun notBooleanToInt(insn: ArithNode, boundArg: RegisterArg): InsnNode {
		val notInsn = InsnNode(InsnType.NOT, 1)
		notInsn.addArg(boundArg.duplicate())
		notInsn.add(AFlag.SYNTHETIC)

		val resType = checkNotNull(insn.getResult()).getType()
		if (resType.canBePrimitive(PrimitiveType.BOOLEAN)) {
			notInsn.setResult(insn.getResult())
			return notInsn
		}
		val notArg = InsnArg.wrapArg(notInsn)
		notArg.setType(ArgType.BOOLEAN)
		val convertInsn = ModVisitor.makeBooleanConvertInsn(checkNotNull(insn.getResult()), notArg, ArgType.INT)
		convertInsn.add(AFlag.SYNTHETIC)
		return convertInsn
	}

	private fun prepareBooleanConvertInsn(resultArg: RegisterArg, boundArg: RegisterArg, useType: ArgType): TernaryInsn {
		val useArg = checkNotNull(boundArg.sVar).assign.duplicate()
		val convertInsn = ModVisitor.makeBooleanConvertInsn(resultArg, useArg, useType)
		convertInsn.add(AFlag.SYNTHETIC)
		return convertInsn
	}

	private fun tryToForceImmutableTypes(mth: MethodNode): Boolean {
		var fixed = false
		for (ssaVar in mth.SVars) {
			val type = ssaVar.typeInfo.getType()
			if (!type.isTypeKnown() && ssaVar.isTypeImmutable()) {
				if (forceImmutableType(ssaVar)) {
					fixed = true
				}
			}
		}
		if (!fixed) {
			return false
		}
		return typeInference.runTypePropagation(mth)
	}

	private fun forceImmutableType(ssaVar: SSAVar): Boolean {
		for (useArg in ssaVar.useList) {
			val parentInsn = useArg.getParentInsn()
			if (parentInsn != null) {
				val insnType = parentInsn.type
				if (insnType == InsnType.AGET || insnType == InsnType.APUT) {
					ssaVar.setType(checkNotNull(ssaVar.immutableType))
					return true
				}
			}
		}
		return false
	}

	override fun getName(): String = "FixTypesVisitor"

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(FixTypesVisitor::class.java)
	}
}
