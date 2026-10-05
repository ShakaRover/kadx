package kadx.core.dex.visitors

import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.core.codegen.ClassGen
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.FieldInitInsnAttr
import kadx.core.dex.info.AccessInfo
import kadx.core.dex.info.FieldInfo
import kadx.core.dex.instructions.IndexInsnNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.InsnWrapArg
import kadx.core.dex.instructions.args.RegisterArg
import kadx.core.dex.nodes.BlockNode
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.InsnNode
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.visitors.shrink.CodeShrinkVisitor
import kadx.core.utils.BlockUtils
import kadx.core.utils.InsnRemover
import kadx.core.utils.ListUtils
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxException
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet

/**
 * 字段初始化提取访问者。
 *
 * **做什么**：把构造器 / `<clinit>` 中重复出现的字段初始化赋值提取为字段声明处的
 * 初始化表达式（`FieldInitInsnAttr`），并据此调整字段顺序。
 *
 * **为什么**：javac 把 `int x = 5;` 编译进构造器，若能识别出“所有构造器都做同样的赋值”，
 * 就可以还原成字段初始化，代码更接近源码。
 */
@KadxVisitor(
	name = "ExtractFieldInit",
	desc = "Move duplicated field initialization from constructors",
	runAfter = [ModVisitor::class],
	runBefore = [ClassModifier::class],
)
class ExtractFieldInit : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		for (inner in cls.innerClasses) {
			visit(inner)
		}
		if (cls.fields.isNotEmpty()) {
			moveStaticFieldsInit(cls)
			moveCommonFieldsInit(cls)
		}
		return false
	}

	/** 单个字段初始化信息。 */
	private class FieldInitInfo(val fieldNode: FieldNode, val putInsn: IndexInsnNode, val canMove: Boolean)

	/** 单个构造器的字段初始化列表。 */
	private class ConstructorInitInfo(val constructorMth: MethodNode, val fieldInits: MutableList<FieldInitInfo>)

	companion object {

		private fun moveStaticFieldsInit(cls: ClassNode) {
			val classInitMth = cls.classInitMth ?: return
			if (!classInitMth.accessFlags.isStatic() ||
				classInitMth.isNoCode() ||
				classInitMth.basicBlocks == null
			) {
				return
			}
			if (ListUtils.noneMatch(cls.fields) { it.isStatic() }) {
				return
			}
			while (processStaticFields(cls, classInitMth)) {
				// 有时移到字段初始化的指令会阻碍变量内联 -> 内联后重试
				CodeShrinkVisitor.shrinkMethod(classInitMth)
			}
		}

		private fun processStaticFields(cls: ClassNode, classInitMth: MethodNode): Boolean {
			val inits = collectFieldsInit(cls, classInitMth, InsnType.SPUT)
			if (inits.isEmpty()) {
				return false
			}
			// 若字段在 clinit 中初始化，则忽略常量值属性
			for (fieldInit in inits) {
				val field = fieldInit.fieldNode
				if (field.accessFlags.isFinal()) {
					field.remove(KadxAttrType.CONSTANT_VALUE)
				}
			}
			filterFieldsInit(inits)
			if (inits.isEmpty()) {
				return false
			}
			for (fieldInit in inits) {
				val insn = fieldInit.putInsn
				val arg = insn.getArg(0)
				if (arg is InsnWrapArg) {
					arg.wrapInsn.add(AFlag.DECLARE_VAR)
				}
				InsnRemover.remove(classInitMth, insn)
				addFieldInitAttr(classInitMth, fieldInit.fieldNode, insn)
			}
			fixFieldsOrder(cls, inits)
			return true
		}

		private fun moveCommonFieldsInit(cls: ClassNode) {
			if (ListUtils.noneMatch(cls.fields) { it.isInstance() }) {
				return
			}
			val constructors = getConstructorsList(cls)
			if (constructors.isEmpty()) {
				return
			}
			val infoList = ArrayList<ConstructorInitInfo>(constructors.size)
			for (constructorMth in constructors) {
				val inits = collectFieldsInit(cls, constructorMth, InsnType.IPUT)
				filterFieldsInit(inits)
				if (inits.isEmpty()) {
					return
				}
				infoList.add(ConstructorInitInfo(constructorMth, inits))
			}
			// 比较收集到的指令
			var common: ConstructorInitInfo? = null
			for (info in infoList) {
				if (common == null) {
					common = info
					continue
				}
				if (!compareFieldInits(common.fieldInits, info.fieldInits)) {
					return
				}
			}
			if (common == null) {
				return
			}
			// 全部检查通过
			for (info in infoList) {
				for (fieldInit in info.fieldInits) {
					val putInsn = fieldInit.putInsn
					val arg = putInsn.getArg(0)
					if (arg is InsnWrapArg) {
						arg.wrapInsn.add(AFlag.DECLARE_VAR)
					}
					InsnRemover.remove(info.constructorMth, putInsn)
				}
			}
			for (fieldInit in common.fieldInits) {
				addFieldInitAttr(common.constructorMth, fieldInit.fieldNode, fieldInit.putInsn)
			}
			fixFieldsOrder(cls, common.fieldInits)
		}

		private fun collectFieldsInit(cls: ClassNode, mth: MethodNode, putType: InsnType): MutableList<FieldInitInfo> {
			val fieldsInit = ArrayList<FieldInitInfo>()
			val singlePathBlocks = HashSet<BlockNode>()
			BlockUtils.visitSinglePath(mth.enterBlock) { block -> singlePathBlocks.add(block) }

			var canReorder = true
			for (block in checkNotNull(mth.basicBlocks)) {
				for (insn in block.instructions) {
					var fieldInsn = false
					if (insn.type == putType) {
						val putInsn = insn as IndexInsnNode
						val field = putInsn.index as FieldInfo
						if (field.declClass == cls.classInfo) {
							val fn = cls.searchField(field)
							if (fn != null) {
								val canMove = canReorder && singlePathBlocks.contains(block)
								fieldsInit.add(FieldInitInfo(fn, putInsn, canMove))
								fieldInsn = true
							}
						}
					}
					if (!fieldInsn && canReorder && !insn.canReorder()) {
						canReorder = false
					}
				}
			}
			return fieldsInit
		}

		private fun filterFieldsInit(inits: MutableList<FieldInitInfo>) {
			// 排除被初始化多次的字段（计数 > 1）
			val counts = HashMap<FieldNode, Int>()
			for (fi in inits) {
				counts[fi.fieldNode] = (counts[fi.fieldNode] ?: 0) + 1
			}
			val excludedFields = HashSet<FieldInfo>()
			for ((field, count) in counts) {
				if (count > 1) {
					excludedFields.add(field.getFieldInfo())
				}
			}

			for (initInfo in inits) {
				if (!checkInsn(initInfo)) {
					excludedFields.add(initInfo.fieldNode.getFieldInfo())
				}
			}
			if (excludedFields.isNotEmpty()) {
				var changed: Boolean
				do {
					changed = false
					for (initInfo in inits) {
						val fieldInfo = initInfo.fieldNode.getFieldInfo()
						if (excludedFields.contains(fieldInfo)) {
							continue
						}
						if (insnUseExcludedField(initInfo, excludedFields)) {
							excludedFields.add(fieldInfo)
							changed = true
						}
					}
				} while (changed)
			}

			// 应用
			if (excludedFields.isNotEmpty()) {
				inits.removeIf { fi -> excludedFields.contains(fi.fieldNode.getFieldInfo()) }
			}
		}

		private fun checkInsn(initInfo: FieldInitInfo): Boolean {
			if (!initInfo.canMove) {
				return false
			}
			val insn = initInfo.putInsn
			val arg = insn.getArg(0)
			if (arg.isInsnWrap) {
				val wrapInsn = (arg as InsnWrapArg).wrapInsn
				if (!wrapInsn.canReorder() && insn.contains(AType.EXC_CATCH)) {
					return false
				}
			} else {
				return arg.isLiteral || arg.isThis()
			}
			val regs = HashSet<RegisterArg>()
			insn.getRegisterArgs(regs)
			if (regs.isNotEmpty()) {
				for (reg in regs) {
					if (!reg.isThis()) {
						return false
					}
				}
			}
			return true
		}

		private fun insnUseExcludedField(initInfo: FieldInitInfo, excludedFields: Set<FieldInfo>): Boolean {
			if (excludedFields.isEmpty()) {
				return false
			}
			val insn = initInfo.putInsn
			val staticField = insn.type == InsnType.SPUT
			val useType = if (staticField) InsnType.SGET else InsnType.IGET
			// 若初始化代码引用了被排除的字段，则排除该字段
			val exclude: Boolean? = insn.visitInsns<Boolean> { innerInsn ->
				var res: Boolean? = null
				if (innerInsn.type == useType) {
					val fieldInfo = (innerInsn as IndexInsnNode).index as FieldInfo
					if (excludedFields.contains(fieldInfo)) {
						res = true
					}
				}
				res
			}
			return exclude == true
		}

		private fun fixFieldsOrder(cls: ClassNode, inits: List<FieldInitInfo>) {
			val orderedFields = processFieldsDependencies(cls, inits)

			// 应用源码行号与别名排序（与方法、内部类相同的排序方式）
			val clsFields = cls.fields as MutableList<FieldNode>
			val sortingMap = HashMap<FieldNode, String>()
			clsFields.sortWith(
				compareBy<FieldNode> { it.sourceLine }
					.thenBy { sortingMap.getOrPut(it) { ClassGen.getShortAlias(it) } },
			)

			if (orderedFields.isNotEmpty()) {
				// 检查是否已经有序
				val ordered = Collections.indexOfSubList(clsFields, orderedFields) != -1
				if (!ordered) {
					clsFields.removeAll(orderedFields)
					clsFields.addAll(orderedFields)
				}
			}
		}

		private fun processFieldsDependencies(cls: ClassNode, inits: List<FieldInitInfo>): List<FieldNode> {
			// 收集依赖字段
			val deps = buildFieldDeps(cls, inits)
			if (deps.isEmpty()) {
				return Collections.emptyList()
			}
			// 构建新列表，把依赖字段放到使用字段之前
			val orderedFields = Utils.collectionMap(inits) { v -> v.fieldNode }
			val result = ArrayList<FieldNode>()
			for (field in orderedFields) {
				val idx = result.indexOf(field)
				val fieldDeps = deps[field]
				if (fieldDeps == null) {
					if (idx == -1) {
						result.add(field)
					}
					continue
				}
				if (idx == -1) {
					for (depField in fieldDeps) {
						if (!result.contains(depField)) {
							result.add(depField)
						}
					}
					result.add(field)
					continue
				}
				for (depField in fieldDeps) {
					val depIdx = result.indexOf(depField)
					if (depIdx == -1) {
						result.add(idx, depField)
					} else if (depIdx > idx) {
						result.removeAt(depIdx)
						result.add(idx, depField)
					}
				}
			}
			return result
		}

		private fun buildFieldDeps(cls: ClassNode, inits: List<FieldInitInfo>): Map<FieldNode, MutableList<FieldNode>> {
			val deps = HashMap<FieldNode, MutableList<FieldNode>>(inits.size)
			for (initInfo in inits) {
				val insn = initInfo.putInsn
				val staticField = insn.type == InsnType.SPUT
				val useType = if (staticField) InsnType.SGET else InsnType.IGET
				insn.visitInsns(
					{ subInsn ->
						if (subInsn.type == useType) {
							val fieldInfo = (subInsn as IndexInsnNode).index as FieldInfo
							if (fieldInfo.declClass == cls.classInfo) {
								val depField = cls.searchField(fieldInfo)
								if (depField != null) {
									deps.getOrPut(initInfo.fieldNode) { ArrayList() }.add(depField)
								}
							}
						}
					},
				)
			}
			return deps
		}

		private fun compareFieldInits(base: List<FieldInitInfo>, other: List<FieldInitInfo>): Boolean {
			if (base.size != other.size) {
				return false
			}
			val count = base.size
			for (i in 0 until count) {
				val baseInsn = base[i].putInsn
				val otherInsn = other[i].putInsn
				if (!sameFieldInit(baseInsn, otherInsn)) {
					return false
				}
			}
			return true
		}

		private fun sameFieldInit(first: InsnNode, second: InsnNode): Boolean {
			if (!first.isSame(second)) {
				return false
			}
			for (i in 0 until first.argsCount) {
				val firstArg = first.getArg(i)
				val secondArg = second.getArg(i)
				// 构造函数可能对同一实例使用不同寄存器 / SSA 变量
				if (firstArg.isThis() && secondArg.isThis()) {
					continue
				}
				if (firstArg.isInsnWrap && secondArg.isInsnWrap) {
					if (!sameFieldInit((firstArg as InsnWrapArg).wrapInsn, (secondArg as InsnWrapArg).wrapInsn)) {
						return false
					}
				} else if (firstArg != secondArg) {
					return false
				}
			}
			return true
		}

		private fun getConstructorsList(cls: ClassNode): List<MethodNode> {
			val list = ArrayList<MethodNode>()
			for (mth in cls.methods) {
				val accFlags: AccessInfo = mth.accessFlags
				if (!accFlags.isStatic() && accFlags.isConstructor()) {
					list.add(mth)
					if (mth.isNoCode() || BlockUtils.isAllBlocksEmpty(mth.basicBlocks)) {
						return Collections.emptyList()
					}
				}
			}
			return list
		}

		private fun addFieldInitAttr(mth: MethodNode, field: FieldNode, putInsn: IndexInsnNode) {
			val fldArg = putInsn.getArg(0)
			val assignInsn = if (fldArg.isInsnWrap) {
				(fldArg as InsnWrapArg).wrapInsn
			} else {
				InsnNode.wrapArg(fldArg)
			}
			field.addAttr(FieldInitInsnAttr(mth, assignInsn))
			if (putInsn.sourceLine != 0) {
				field.setSourceLine(putInsn.sourceLine)
			}
		}
	}
}
