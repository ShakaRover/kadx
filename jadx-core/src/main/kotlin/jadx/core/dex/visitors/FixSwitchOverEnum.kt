package jadx.core.dex.visitors

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.AType
import jadx.core.dex.attributes.nodes.EnumClassAttr
import jadx.core.dex.attributes.nodes.EnumMapAttr
import jadx.core.dex.attributes.nodes.RegionRefAttr
import jadx.core.dex.info.AccessInfo
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.SwitchInsn
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.regions.SwitchRegion
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxException
import java.util.HashMap

/**
 * 枚举 switch 简化访问者。
 *
 * **做什么**：识别对枚举的 switch 语句中编译器生成的合成映射代码，并还原为
 * 直接 `switch (enumVar)` 的形式。支持两种模式：
 * 1. `enum.ordinal()` 直接映射（[processDirectEnumSwitch]）；
 * 2. 通过合成 `$SwitchMap$...` 数组重映射（[processRemappedEnumSwitch]）。
 *
 * **为什么**：javac 编译枚举 switch 时会生成额外的合成类和数组，还原后代码更简洁，
 * 也能把合成类标记为不生成。
 */
@JadxVisitor(
	name = "FixSwitchOverEnum",
	desc = "Simplify synthetic code in switch over enum",
	runAfter = [CodeShrinkVisitor::class, EnumVisitor::class],
)
class FixSwitchOverEnum : AbstractVisitor() {

	override fun visit(cls: ClassNode): Boolean {
		initClsEnumMap(cls)
		return true
	}

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		var changed = false
		for (block in checkNotNull(mth.basicBlocks)) {
			for (insn in block.getInstructions()) {
				if (insn.type == InsnType.SWITCH && !insn.contains(AFlag.REMOVE)) {
					if (processEnumSwitch(mth, insn as SwitchInsn)) {
						changed = true
					}
				}
			}
		}
		if (changed) {
			CodeShrinkVisitor.shrinkMethod(mth)
		}
	}

	companion object {

		private fun processEnumSwitch(mth: MethodNode, insn: SwitchInsn): Boolean {
			val arg = insn.getArg(0)
			if (!arg.isInsnWrap) {
				return false
			}
			val wrapInsn = (arg as InsnWrapArg).wrapInsn
			when (wrapInsn.type) {
				InsnType.AGET -> return processRemappedEnumSwitch(mth, insn, wrapInsn, arg)
				InsnType.INVOKE -> return processDirectEnumSwitch(mth, insn, wrapInsn as InvokeNode, arg)
				else -> {}
			}
			return false
		}

		private fun executeReplace(
			swInsn: SwitchInsn,
			arg: InsnArg,
			invVar: InsnArg,
			caseReplace: (Int) -> Any?,
		): Boolean {
			val regionRefAttr = swInsn.get(AType.REGION_REF) ?: return false
			if (!swInsn.replaceArg(arg, invVar)) {
				return false
			}
			val replaceMap = HashMap<Any?, Any?>()
			val caseCount = swInsn.getKeys().size
			for (i in 0 until caseCount) {
				val key = swInsn.getKey(i)
				val replaceObj = caseReplace(i)
				swInsn.modifyKey(i, replaceObj)
				replaceMap[key] = replaceObj
			}
			val region = regionRefAttr.region as SwitchRegion
			for (caseInfo in region.cases) {
				val keys = caseInfo.keys as MutableList<Any>
				for (j in keys.indices) {
					val k = keys[j]
					keys[j] = Utils.getOrElse(replaceMap[k], k)
				}
			}
			return true
		}

		private fun processDirectEnumSwitch(mth: MethodNode, swInsn: SwitchInsn, invInsn: InvokeNode, arg: InsnArg): Boolean {
			val callMth = invInsn.callMth
			if (callMth.shortId != "ordinal()I") {
				return false
			}
			val invVar = invInsn.getArg(0)
			val enumCls = mth.root().resolveClass(invVar.getType()) ?: return false
			val enumClassAttr = enumCls.get(AType.ENUM_CLASS) ?: return false
			val casesReplaceArr = mapToCases(swInsn, enumClassAttr.fields) ?: return false
			return executeReplace(swInsn, arg, invVar) { i -> casesReplaceArr[i] }
		}

		private fun mapToCases(swInsn: SwitchInsn, fields: List<EnumClassAttr.EnumField>): Array<FieldNode?>? {
			val caseCount = swInsn.getKeys().size
			if (fields.size < caseCount) {
				return null
			}
			val casesMap = arrayOfNulls<FieldNode>(caseCount)
			for (i in 0 until caseCount) {
				val key = swInsn.getKey(i)
				if (key is Int) {
					try {
						casesMap[key] = fields[key].field
					} catch (e: Exception) {
						return null
					}
				} else {
					return null
				}
			}
			return casesMap
		}

		private fun processRemappedEnumSwitch(mth: MethodNode, insn: SwitchInsn, wrapInsn: InsnNode, arg: InsnArg): Boolean {
			val enumMapInfo = checkEnumMapAccess(mth.root(), wrapInsn) ?: return false
			val enumMapField = enumMapInfo.mapField
			val invArg = enumMapInfo.arg

			val valueMap = getEnumMap(enumMapField) ?: return false
			val caseCount = insn.getKeys().size
			for (i in 0 until caseCount) {
				val key = insn.getKey(i)
				val newKey = valueMap.get(key)
				if (newKey == null) {
					return false
				}
			}
			if (executeReplace(insn, arg, invArg) { i -> valueMap.get(insn.getKey(i)) }) {
				enumMapField.add(AFlag.DONT_GENERATE)
				checkAndHideClass(enumMapField.parentClass)
				return true
			}
			return false
		}

		private fun initClsEnumMap(enumCls: ClassNode) {
			val clsInitMth = enumCls.classInitMth
			if (clsInitMth == null || clsInitMth.isNoCode()) {
				return
			}
			val blocks = clsInitMth.basicBlocks ?: return
			val mapAttr = EnumMapAttr()
			for (block in blocks) {
				for (insn in block.getInstructions()) {
					if (insn.type == InsnType.APUT) {
						addToEnumMap(enumCls.root(), mapAttr, insn)
					}
				}
			}
			if (!mapAttr.isEmpty()) {
				enumCls.addAttr(mapAttr)
			}
		}

		private fun getEnumMap(field: FieldNode): EnumMapAttr.KeyValueMap? {
			val syntheticClass = field.parentClass
			val mapAttr = syntheticClass.get(AType.ENUM_MAP) ?: return null
			return mapAttr.getMap(field)
		}

		private fun addToEnumMap(root: RootNode, mapAttr: EnumMapAttr, aputInsn: InsnNode) {
			val litArg = aputInsn.getArg(2)
			if (!litArg.isLiteral) {
				return
			}
			val mapInfo = checkEnumMapAccess(root, aputInsn) ?: return
			val enumArg = mapInfo.arg
			val field = mapInfo.mapField
			if (field == null || !enumArg.isInsnWrap) {
				return
			}
			val sget = (enumArg as InsnWrapArg).wrapInsn
			if (sget !is IndexInsnNode) {
				return
			}
			val index = sget.index
			if (index !is FieldInfo) {
				return
			}
			val fieldNode = root.resolveField(index) ?: return
			val literal = (litArg as LiteralArg).literal.toInt()
			mapAttr.add(field, literal, fieldNode)
		}

		private fun checkEnumMapAccess(root: RootNode, checkInsn: InsnNode): EnumMapInfo? {
			val sgetArg = checkInsn.getArg(0)
			val invArg = checkInsn.getArg(1)
			if (!sgetArg.isInsnWrap || !invArg.isInsnWrap) {
				return null
			}
			val invInsn = (invArg as InsnWrapArg).wrapInsn
			val sgetInsn = (sgetArg as InsnWrapArg).wrapInsn
			if (invInsn.type != InsnType.INVOKE || sgetInsn.type != InsnType.SGET) {
				return null
			}
			val inv = invInsn as InvokeNode
			if (inv.callMth.shortId != "ordinal()I") {
				return null
			}
			val enumCls = root.resolveClass(inv.callMth.declClass)
			if (enumCls == null || !enumCls.isEnum()) {
				return null
			}
			val index = (sgetInsn as IndexInsnNode).index
			if (index !is FieldInfo) {
				return null
			}
			val enumMapField = root.resolveField(index)
			if (enumMapField == null || !enumMapField.accessFlags.isSynthetic()) {
				return null
			}
			return EnumMapInfo(inv.getArg(0), enumMapField)
		}

		/**
		 * 若所有 static final 合成字段都标记为 DONT_GENERATE => 隐藏整个类。
		 */
		private fun checkAndHideClass(cls: ClassNode) {
			for (field in cls.fields) {
				val af: AccessInfo = field.accessFlags
				if (af.isSynthetic() && af.isStatic() && af.isFinal() &&
					!field.contains(AFlag.DONT_GENERATE)
				) {
					return
				}
			}
			cls.add(AFlag.DONT_GENERATE)
		}
	}

	private class EnumMapInfo(val arg: InsnArg, val mapField: FieldNode)
}
