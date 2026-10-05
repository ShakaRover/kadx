package kadx.gui.ui.graphs

import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.clsp.ClspClass
import kadx.core.clsp.ClspGraph
import kadx.core.dex.nodes.RootNode

/**
 * 继承关系缓存属性（PR #2935）。
 *
 * **做什么**：在 [RootNode] 上挂载一份“类名 -> 直接父类/接口”与
 * “类名 -> 直接子类/实现类”的双向映射，供继承图对话框快速遍历。
 *
 * **为什么挂在 RootNode 上**：构建映射需要遍历整个 classpath 图，成本较高；
 * 作为属性缓存后，同一工程内多次打开继承图只构建一次。
 *
 * **线程模型**：与其它 kadx 属性一致，构建/读取都在后台分析线程，无额外同步。
 */
class InheritanceDataAttr private constructor(clsp: ClspGraph) : IKadxAttribute {

	/**
	 * 类名 -> 直接父类/接口集合。
	 * 等价于 nameMap 中 [ClspClass] 的 parents 属性。
	 */
	private val immediateSuperTypesCache: Map<String, Set<String>>

	/** 类名 -> 直接子类/实现类列表。 */
	private val immediateImplementsCache: Map<String, List<String>>

	init {
		immediateSuperTypesCache = buildImmediateSuperTypesCache(clsp)
		immediateImplementsCache = buildImmediateImplementsCache(clsp)
	}

	/** 获取某个类的直接父类/接口（无则为空集合）。 */
	fun getParents(clsName: String): Set<String> = immediateSuperTypesCache[clsName] ?: emptySet()

	/** 获取某个类的直接子类/实现类（无则为空列表）。 */
	fun getChildren(clsName: String): List<String> = immediateImplementsCache[clsName] ?: emptyList()

	override val attrType: IKadxAttrType<*> get() = INHERITANCE_DATA

	/**
	 * 遍历 nameMap 并读取每个 [ClspClass] 的 parents，构建“类名 -> 直接父类”映射。
	 */
	private fun buildImmediateSuperTypesCache(clspGraph: ClspGraph): Map<String, Set<String>> {
		val nameMap = checkNotNull(clspGraph.getClsNameMap())
		val nameToSupertypesMap = HashMap<String, Set<String>>(nameMap.size)
		for (cls in nameMap.values) {
			val supertypesSet = HashSet<String>()
			addImmediateSuperTypes(clspGraph, cls, supertypesSet)
			nameToSupertypesMap[cls.name] = supertypesSet
		}
		return nameToSupertypesMap
	}

	/**
	 * 反转“类名 -> 直接父类”映射，构建“类名 -> 直接子类”映射。
	 */
	private fun buildImmediateImplementsCache(clsp: ClspGraph): Map<String, List<String>> {
		val nameMap = checkNotNull(clsp.getClsNameMap())
		val map = HashMap<String, MutableList<String>>(nameMap.size)
		val classes = ArrayList(nameMap.keys)
		classes.sort()
		for (cls in classes) {
			for (st in getParents(cls)) {
				map.computeIfAbsent(st) { ArrayList() }.add(cls)
			}
		}
		return map
	}

	/** 仅把 cls 的直接父类/接口的名字加入 [result]。 */
	private fun addImmediateSuperTypes(clspGraph: ClspGraph, cls: ClspClass, result: MutableSet<String>) {
		val parents = cls.parents ?: return
		for (parentType in parents) {
			if (parentType == null) {
				continue
			}
			val parentCls = clspGraph.getClsDetails(parentType)
			if (parentCls != null) {
				// 等价于 parentType.getObject()
				result.add(parentCls.name)
			} else {
				// 父类型未知
				result.add(parentType.getObject())
			}
		}
	}

	companion object {
		val INHERITANCE_DATA: IKadxAttrType<InheritanceDataAttr> = IKadxAttrType.create("INHERITANCE_DATA")

		/** 获取（或按需构建）挂在 [root] 上的继承关系缓存。 */
		@JvmStatic
		fun get(root: RootNode): InheritanceDataAttr {
			root.get(INHERITANCE_DATA)?.let { return it }
			val attr = InheritanceDataAttr(checkNotNull(root.getClsp()))
			root.addAttr(attr)
			return attr
		}
	}
}
