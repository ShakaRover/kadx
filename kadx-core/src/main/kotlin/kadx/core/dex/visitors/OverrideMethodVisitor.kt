package kadx.core.dex.visitors

import kadx.core.clsp.ClspClass
import kadx.core.clsp.ClspMethod
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.MethodBridgeAttr
import kadx.core.dex.attributes.nodes.MethodOverrideAttr
import kadx.core.dex.attributes.nodes.RenameReasonAttr
import kadx.core.dex.info.AccessInfo
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.IMethodDetails
import kadx.core.dex.nodes.MethodNode
import kadx.core.dex.nodes.RootNode
import kadx.core.dex.visitors.rename.RenameVisitor
import kadx.core.dex.visitors.typeinference.TypeCompare
import kadx.core.dex.visitors.typeinference.TypeCompareEnum
import kadx.core.dex.visitors.typeinference.TypeInferenceVisitor
import kadx.core.utils.ListUtils
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxException
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedHashSet
import java.util.Objects
import java.util.SortedSet
import java.util.TreeSet

/**
 * 标记覆写方法，并还原被泛型擦除的返回/参数类型。
 *
 * **做什么**：对每个类收集其父类/接口层级，找出被覆写的方法，
 * 建立 [MethodOverrideAttr]（含相关方法集合），并根据基类方法把泛型类型写回
 * （例如基类 `T get()`，子类还原为 `String get()`）。
 *
 * **为什么在类型推导与重命名之前**：还原类型会影响后续类型推导；覆写关系也会影响重命名。
 */
@KadxVisitor(
	name = "OverrideMethodVisitor",
	desc = "Mark override methods and revert type erasure",
	runBefore = [
		TypeInferenceVisitor::class,
		RenameVisitor::class,
	],
)
class OverrideMethodVisitor : AbstractVisitor() {

	@Throws(KadxException::class)
	override fun visit(cls: ClassNode): Boolean {
		val superData = collectSuperTypes(cls)
		if (superData != null) {
			for (mth in cls.methods) {
				processMth(mth, superData)
			}
		}
		return true
	}

	override fun getName(): String = "OverrideMethodVisitor"

	private fun processMth(mth: MethodNode, superData: SuperTypesData) {
		if (mth.isConstructor() || mth.accessFlags.isStatic() || mth.accessFlags.isPrivate()) {
			return
		}
		val attr = processOverrideMethods(mth, superData)
		if (attr != null) {
			if (attr.baseMethods.isEmpty()) {
				throw KadxRuntimeException("No base methods for override attribute: " + attr.overrideList)
			}
			mth.addAttr(attr)
			val baseMth = Utils.getOne(attr.baseMethods)
			if (baseMth != null) {
				var updated = fixMethodReturnType(mth, baseMth, superData)
				updated = fixMethodArgTypes(mth, baseMth, superData) || updated
				if (updated) {
					// 检查新签名是否造成方法冲突
					checkMethodSignatureCollisions(mth, mth.root().getArgs().isRenameValid)
				}
			}
		}
	}

	private fun processOverrideMethods(mth: MethodNode, superData: SuperTypesData): MethodOverrideAttr? {
		val existing = mth.get(AType.METHOD_OVERRIDE)
		if (existing != null) {
			return existing
		}
		val cls = mth.parentClass
		val signature = mth.methodInfo.makeSignature(false)
		val overrideList = ArrayList<IMethodDetails>()
		val baseMethods = HashSet<IMethodDetails>()
		for (superType in superData.superTypes) {
			val classNode = mth.root().resolveClass(superType)
			if (classNode != null) {
				val ovrdMth = searchOverriddenMethod(classNode, mth, signature)
				if (ovrdMth != null) {
					if (isMethodVisibleInCls(ovrdMth, cls)) {
						overrideList.add(ovrdMth)
						val attr = ovrdMth.get(AType.METHOD_OVERRIDE)
						if (attr != null) {
							addBaseMethod(superData, overrideList, baseMethods, superType)
							return buildOverrideAttr(mth, overrideList, baseMethods, attr)
						}
					}
				}
			} else {
				val clsDetails = mth.root().getClsp()?.getClsDetails(superType)
				if (clsDetails != null) {
					val methodsMap = clsDetails.methodsMap
					for ((mthShortId, value) in methodsMap) {
						// 不校验完整签名，classpath 方法可信（同一类中不会出现同签名方法）
						if (mthShortId.startsWith(signature)) {
							overrideList.add(value)
							break
						}
					}
				}
			}
			addBaseMethod(superData, overrideList, baseMethods, superType)
		}
		return buildOverrideAttr(mth, overrideList, baseMethods, null)
	}

	private fun addBaseMethod(
		superData: SuperTypesData,
		overrideList: List<IMethodDetails>,
		baseMethods: MutableSet<IMethodDetails>,
		superType: ArgType,
	) {
		if (superData.endTypes.contains(superType.getObject())) {
			val last = Utils.last(overrideList)
			if (last != null) {
				baseMethods.add(last)
			}
		}
	}

	private fun searchOverriddenMethod(cls: ClassNode, mth: MethodNode, signature: String): MethodNode? {
		// 用包含返回值的完整签名精确匹配，抵抗混淆（见测试 'TestOverrideWithSameName'）
		val shortId = mth.methodInfo.shortId
		for (supMth in cls.methods) {
			if (supMth.methodInfo.shortId == shortId && !supMth.accessFlags.isStatic()) {
				return supMth
			}
		}
		// 用不含返回值、但返回值更宽泛的签名匹配
		for (supMth in cls.methods) {
			if (supMth.methodInfo.shortId.startsWith(signature) && !supMth.accessFlags.isStatic()) {
				val typeCompare: TypeCompare = cls.root().typeCompare
				val supRetType = supMth.methodInfo.returnType
				val mthRetType = mth.methodInfo.returnType
				val res = typeCompare.compareTypes(supRetType, mthRetType)
				if (res.isWider()) {
					return supMth
				}
				if (res == TypeCompareEnum.UNKNOWN || res == TypeCompareEnum.CONFLICT) {
					mth.addDebugComment("Possible override for method " + supMth.methodInfo.fullId)
				}
			}
		}
		return null
	}

	private fun buildOverrideAttr(
		mth: MethodNode,
		overrideList: List<IMethodDetails>,
		baseMethods: Set<IMethodDetails>,
		attr: MethodOverrideAttr?,
	): MethodOverrideAttr? {
		if (overrideList.isEmpty() && attr == null) {
			return null
		}
		if (attr == null) {
			// 追溯到基类方法
			val cleanOverrideList = ListUtils.distinctList(overrideList)
			return applyOverrideAttr(mth, cleanOverrideList, baseMethods, false)
		}
		// 追踪到已处理的方法 -> 开始合并
		val mergedOverrideList = Utils.mergeLists(overrideList, attr.overrideList)
		val cleanOverrideList = ListUtils.distinctList(checkNotNull(mergedOverrideList))
		val mergedBaseMethods = Utils.mergeSets(baseMethods, attr.baseMethods)
		return applyOverrideAttr(mth, cleanOverrideList, checkNotNull(mergedBaseMethods), true)
	}

	private fun applyOverrideAttr(
		mth: MethodNode,
		overrideList: List<IMethodDetails>,
		baseMethods: Set<IMethodDetails>,
		update: Boolean,
	): MethodOverrideAttr {
		// 若覆写列表包含未解析的方法，则不重命名
		val dontRename = overrideList.any { it !is MethodNode }
		var relatedMethods: SortedSet<MethodNode>? = null
		val mthNodes = getMethodNodes(mth, overrideList)
		if (update) {
			// 合并所有 override 属性中的相关方法
			for (mthNode in mthNodes) {
				val ovrdAttr = mthNode.get(AType.METHOD_OVERRIDE)
				if (ovrdAttr != null) {
					// 复用已分配集合之一
					relatedMethods = ovrdAttr.relatedMthNodes
					break
				}
			}
			if (relatedMethods != null) {
				relatedMethods.addAll(mthNodes)
			} else {
				relatedMethods = TreeSet(mthNodes)
			}
			for (mthNode in mthNodes) {
				val ovrdAttr = mthNode.get(AType.METHOD_OVERRIDE)
				if (ovrdAttr != null) {
					val set = ovrdAttr.relatedMthNodes
					if (relatedMethods !== set) {
						relatedMethods.addAll(set)
					}
				}
			}
		} else {
			relatedMethods = TreeSet(mthNodes)
		}

		var depth = 0
		for (mthNode in mthNodes) {
			if (dontRename) {
				mthNode.add(AFlag.DONT_RENAME)
			}
			if (depth == 0) {
				// 跳过当前（第一个）方法
				depth = 1
				continue
			}
			if (update) {
				val ovrdAttr = mthNode.get(AType.METHOD_OVERRIDE)
				if (ovrdAttr != null) {
					ovrdAttr.relatedMthNodes = relatedMethods
					continue
				}
			}
			mthNode.addAttr(MethodOverrideAttr(Utils.listTail(overrideList, depth), relatedMethods, baseMethods))
			depth++
		}
		return MethodOverrideAttr(overrideList, relatedMethods, baseMethods)
	}

	private fun getMethodNodes(mth: MethodNode, overrideList: List<IMethodDetails>): List<MethodNode> {
		val list = ArrayList<MethodNode>(1 + overrideList.size)
		list.add(mth)
		for (md in overrideList) {
			if (md is MethodNode) {
				list.add(md)
			}
		}
		return list
	}

	/**
	 * 注意：这是 ModVisitor.isFieldVisibleInMethod 的简化版本
	 */
	private fun isMethodVisibleInCls(superMth: MethodNode, cls: ClassNode): Boolean {
		val accessFlags: AccessInfo = superMth.accessFlags
		if (accessFlags.isPrivate()) {
			return false
		}
		if (accessFlags.isPublic() || accessFlags.isProtected()) {
			return true
		}
		// package-private
		return Objects.equals(superMth.parentClass.`package`, cls.`package`)
	}

	private class SuperTypesData(
		val superTypes: List<ArgType>,
		val endTypes: Set<String>,
	)

	private fun collectSuperTypes(cls: ClassNode): SuperTypesData? {
		val superTypes = LinkedHashSet<ArgType>()
		val endTypes = HashSet<String>()
		collectSuperTypes(cls, superTypes, endTypes)
		if (superTypes.isEmpty()) {
			return null
		}
		if (endTypes.isEmpty()) {
			throw KadxRuntimeException("No end types in class hierarchy: $cls")
		}
		return SuperTypesData(ArrayList(superTypes), endTypes)
	}

	private fun collectSuperTypes(cls: ClassNode, superTypes: MutableSet<ArgType>, endTypes: MutableSet<String>) {
		val root = cls.root()
		var k = 0
		val superClass = cls.superClass
		if (superClass != null) {
			k += addSuperType(root, superTypes, endTypes, superClass)
		}
		for (iface in cls.interfaces) {
			k += addSuperType(root, superTypes, endTypes, iface)
		}
		if (k == 0) {
			endTypes.add(cls.getType().getObject())
		}
	}

	private fun addSuperType(
		root: RootNode,
		superTypes: MutableSet<ArgType>,
		endTypes: MutableSet<String>,
		superType: ArgType,
	): Int {
		if (Objects.equals(superType, ArgType.OBJECT)) {
			return 0
		}
		if (!superTypes.add(superType)) {
			// 发现 'super' 循环，停止处理
			return 0
		}
		val classNode = root.resolveClass(superType)
		if (classNode != null) {
			collectSuperTypes(classNode, superTypes, endTypes)
			return 1
		}
		val clsDetails = root.getClsp()?.getClsDetails(superType)
		if (clsDetails != null) {
			var k = 0
			for (parentType in checkNotNull(clsDetails.parents)) {
				k += addSuperType(root, superTypes, endTypes, checkNotNull(parentType))
			}
			if (k == 0) {
				endTypes.add(superType.getObject())
			}
			return 1
		}
		// 未找到信息 => 视为层级末端
		endTypes.add(superType.getObject())
		return 1
	}

	private fun fixMethodReturnType(mth: MethodNode, baseMth: IMethodDetails, superData: SuperTypesData): Boolean {
		val returnType = mth.returnType
		if (returnType == ArgType.VOID) {
			return false
		}
		val updated = updateReturnType(mth, baseMth, superData)
		if (updated) {
			mth.addDebugComment("Return type fixed from '$returnType' to match base method")
		}
		return updated
	}

	private fun updateReturnType(mth: MethodNode, baseMth: IMethodDetails, superData: SuperTypesData): Boolean {
		val baseReturnType = baseMth.returnType
		if (mth.returnType == baseReturnType) {
			return false
		}
		if (!baseReturnType.containsTypeVariable()) {
			return false
		}
		val typeCompare = mth.root().typeCompare
		val baseCls = baseMth.methodInfo.declClass.type
		for (superType in superData.superTypes) {
			val compareResult = typeCompare.compareTypes(superType, baseCls)
			if (compareResult == TypeCompareEnum.NARROW_BY_GENERIC) {
				val targetRetType = mth.root().getTypeUtils().replaceClassGenerics(superType, baseReturnType)
				if (targetRetType != null &&
					!targetRetType.containsTypeVariable() &&
					targetRetType != mth.returnType
				) {
					mth.updateReturnType(targetRetType)
					return true
				}
			}
		}
		return false
	}

	private fun fixMethodArgTypes(mth: MethodNode, baseMth: IMethodDetails, superData: SuperTypesData): Boolean {
		val mthArgTypes = mth.argTypes
		val baseArgTypes = baseMth.argTypes
		if (mthArgTypes == baseArgTypes) {
			return false
		}
		val argCount = mthArgTypes.size
		if (argCount != baseArgTypes.size) {
			return false
		}
		var changed = false
		val newArgTypes = ArrayList<ArgType>(argCount)
		for (argNum in 0 until argCount) {
			val newType = updateArgType(mth, baseMth, superData, argNum)
			if (newType != null) {
				changed = true
				newArgTypes.add(newType)
			} else {
				newArgTypes.add(mthArgTypes[argNum])
			}
		}
		if (changed) {
			mth.updateArgTypes(newArgTypes, "Method arguments types fixed to match base method")
		}
		return changed
	}

	private fun updateArgType(mth: MethodNode, baseMth: IMethodDetails, superData: SuperTypesData, argNum: Int): ArgType? {
		val arg = mth.argTypes[argNum]
		val baseArg = baseMth.argTypes[argNum]
		if (arg == baseArg) {
			return null
		}
		if (!baseArg.containsTypeVariable()) {
			return null
		}
		val typeCompare = mth.root().typeCompare
		val baseCls = baseMth.methodInfo.declClass.type
		for (superType in superData.superTypes) {
			val compareResult = typeCompare.compareTypes(superType, baseCls)
			if (compareResult == TypeCompareEnum.NARROW_BY_GENERIC) {
				val targetArgType = mth.root().getTypeUtils().replaceClassGenerics(superType, baseArg)
				if (targetArgType != null &&
					!targetArgType.containsTypeVariable() &&
					targetArgType != arg
				) {
					return targetArgType
				}
			}
		}
		return null
	}

	private fun checkMethodSignatureCollisions(mth: MethodNode, rename: Boolean) {
		val mthName = mth.methodInfo.alias
		val newSignature = MethodInfo.makeShortId(mthName, mth.argTypes, null)
		for (otherMth in mth.parentClass.methods) {
			val otherMthName = otherMth.alias
			if (otherMthName == mthName && otherMth !== mth) {
				val otherSignature = otherMth.methodInfo.makeSignature(true, false)
				if (otherSignature == newSignature) {
					if (rename) {
						if (otherMth.contains(AFlag.DONT_RENAME) || otherMth.contains(AType.METHOD_OVERRIDE)) {
							otherMth.addWarnComment("Can't rename method to resolve collision")
						} else {
							otherMth.methodInfo.alias = makeNewAlias(otherMth)
							otherMth.addAttr(RenameReasonAttr("avoid collision after fix types in other method"))
						}
					}
					otherMth.addAttr(MethodBridgeAttr(mth))
					return
				}
			}
		}
	}

	// TODO: at this point deobfuscator is not available and map file already saved
	private fun makeNewAlias(mth: MethodNode): String {
		val cls = mth.parentClass
		val baseName = mth.alias
		var k = 2
		while (true) {
			val alias = baseName + k
			val methodNode = cls.searchMethodByShortName(alias)
			if (methodNode == null) {
				return alias
			}
			k++
		}
	}
}
