package jadx.core.dex.visitors

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.nodes.BlockNode
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.dex.visitors.shrink.CodeShrinkVisitor
import jadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import jadx.core.utils.exceptions.JadxException
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

/**
 * 修正被“字段遮蔽（shadowing）”的字段访问。
 *
 * **场景**：子类与父类存在同名字段时，源码里直接写 `field` 会指向子类字段；
 * 若实际要访问父类字段，就必须写成 `super.field`（唯一父类场景）或
 * 显式把实例 cast 成声明类（多父类场景）。
 *
 * **做什么**：[init] 阶段为每个类收集同名字段集合并决定修正方式；
 * [visit] 阶段在方法里把对应的 `iget/iput` 加上 [AFlag.SUPER] 或插入 cast。
 */
@JadxVisitor(
	name = "ShadowFieldVisitor",
	desc = "Fix shadowed field access",
	runAfter = [TypeInferenceVisitor::class],
	runBefore = [CodeShrinkVisitor::class],
)
class ShadowFieldVisitor : AbstractVisitor() {

	private lateinit var fixInfoMap: Map<String, FieldFixInfo>

	override fun init(root: RootNode) {
		val map = HashMap<String, FieldFixInfo>()
		for (cls in root.getClasses(true)) {
			val fieldFixMap = searchShadowedFields(cls)
			if (fieldFixMap.isNotEmpty()) {
				val fixInfo = FieldFixInfo()
				fixInfo.fieldFixMap = fieldFixMap
				map[cls.rawName] = fixInfo
			}
		}
		this.fixInfoMap = map
	}

	@Throws(JadxException::class)
	override fun visit(mth: MethodNode) {
		if (mth.isNoCode()) {
			return
		}
		fixShadowFieldAccess(mth, fixInfoMap)
	}

	private class FieldFixInfo {
		lateinit var fieldFixMap: Map<FieldInfo, FieldFixType>
	}

	private enum class FieldFixType {
		SUPER,
		CAST,
	}

	companion object {
		private fun searchShadowedFields(thisCls: ClassNode): Map<FieldInfo, FieldFixType> {
			val allFields = collectAllInstanceFields(thisCls)
			if (allFields.isEmpty()) {
				return emptyMap()
			}
			val mapByName = groupByName(allFields)
			mapByName.entries.removeAll { it.value.size == 1 }
			if (mapByName.isEmpty()) {
				return emptyMap()
			}
			val fixMap = HashMap<FieldInfo, FieldFixType>()
			for (fields in mapByName.values) {
				// Java 里用 == 比较对象引用，这里保持 ===
				val fromThisCls = fields[0].parentClass === thisCls
				if (fromThisCls && fields.size == 2) {
					// 只有一个父类含同名字段 => 可以用 super
					val otherField = fields[1]
					if (otherField.parentClass !== thisCls) {
						fixMap[otherField.fieldInfo] = FieldFixType.SUPER
					}
				} else {
					// 多个父类含同名字段 => 不能用 super，需要 cast 到确切类型
					for (field in fields) {
						if (field.parentClass !== thisCls) {
							fixMap[field.fieldInfo] = FieldFixType.CAST
						}
					}
				}
			}
			return fixMap
		}

		private fun groupByName(allFields: List<FieldNode>): MutableMap<String, MutableList<FieldNode>> {
			val groupByName = HashMap<String, MutableList<FieldNode>>(allFields.size)
			for (field in allFields) {
				groupByName.getOrPut(field.name) { ArrayList() }.add(field)
			}
			return groupByName
		}

		private fun collectAllInstanceFields(cls: ClassNode): List<FieldNode> {
			val fieldsList = ArrayList<FieldNode>()
			val visited = HashSet<ClassNode>()
			var currentClass: ClassNode? = cls
			while (currentClass != null) {
				if (!visited.add(currentClass)) {
					val msg = "Found 'super' loop in classes: $visited"
					visited.forEach { c -> c.addWarnComment(msg) }
					return fieldsList
				}
				for (field in currentClass.fields) {
					if (!field.accessFlags.isStatic()) {
						fieldsList.add(field)
					}
				}
				val superClass = currentClass.superClass ?: break
				currentClass = cls.root().resolveClass(superClass)
			}
			return fieldsList
		}

		private fun fixShadowFieldAccess(mth: MethodNode, fixInfoMap: Map<String, FieldFixInfo>) {
			for (block in checkNotNull(mth.basicBlocks)) {
				for (insn in block.getInstructions()) {
					processInsn(mth, insn, fixInfoMap)
				}
			}
		}

		private fun processInsn(mth: MethodNode, insn: InsnNode, fixInfoMap: Map<String, FieldFixInfo>) {
			val fieldInfo = getFieldInfo(insn) ?: return
			val arg = insn.getArg(insn.argsCount - 1)
			val type = arg.getType()
			if (!type.isTypeKnown() || !type.isObject()) {
				return
			}
			val fieldFixInfo = fixInfoMap[type.getObject()] ?: return
			val fieldFixType = fieldFixInfo.fieldFixMap[fieldInfo] ?: return
			fixFieldAccess(mth, fieldInfo, fieldFixType, arg)
		}

		private fun getFieldInfo(insn: InsnNode): FieldInfo? = when (insn.type) {
			InsnType.IPUT, InsnType.IGET -> (insn as IndexInsnNode).index as FieldInfo
			else -> null
		}

		private fun fixFieldAccess(mth: MethodNode, fieldInfo: FieldInfo, fieldFixType: FieldFixType, arg: InsnArg) {
			if (fieldFixType == FieldFixType.SUPER) {
				if (arg.isThis()) {
					// 把 'this' 转成 'super'
					arg.add(AFlag.SUPER)
					return
				}
			}
			// 应用 cast
			val castInsn = IndexInsnNode(InsnType.CAST, fieldInfo.declClass.type, 1)
			castInsn.addArg(arg.duplicate())
			castInsn.add(AFlag.SYNTHETIC)
			castInsn.add(AFlag.EXPLICIT_CAST)
			arg.wrapInstruction(mth, castInsn, false)
		}
	}
}
