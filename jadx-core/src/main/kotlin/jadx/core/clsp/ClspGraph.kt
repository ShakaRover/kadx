package jadx.core.clsp

import jadx.core.Consts
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.IMethodDetails
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.exceptions.DecodeException
import jadx.core.utils.exceptions.JadxRuntimeException
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.Collections

/**
 * 类层次结构图（classpath graph），附带方法附加信息。
 *
 * **核心职责**：
 * - 从 `.jcst` 文件或应用类加载所有类的层次信息（[loadClsSetFile] / [addApp]）；
 * - 提供“某类是否实现某接口”“某类的所有父类型”“某方法在父类中的定义”等查询；
 * - 维护“缺失类”集合，便于最后统一打印警告。
 *
 * **缓存**：父类型集合 [superTypesCache] 与实现类集合 [implementsCache] 需要显式调用
 * [initCache] 构建，避免每次查询都遍历继承链。
 *
 * **Kotlin 转换说明**：原 Java 字段 `nameMap/superTypesCache/implementsCache` 在初始化前
 * 可能为 null，因此如实声明为可空并用 `checkNotNull` 在访问点还原 NPE 语义；
 * 静态入口 [loadClsSetFile] 等保留 `@Throws` 以维持 Java 调用方的受检异常声明。
 */
class ClspGraph(private val root: RootNode) {
	private var nameMap: MutableMap<String, ClspClass>? = null
	private var superTypesCache: MutableMap<String, Set<String>>? = null
	private var implementsCache: MutableMap<String, MutableList<String>>? = null

	private val missingClasses: MutableSet<String> = HashSet()

	@Throws(IOException::class, DecodeException::class)
	fun loadClsSetFile() {
		val set = ClsSet(root)
		set.loadFromClstFile()
		addClasspath(set)
	}

	fun addClasspath(set: ClsSet) {
		if (nameMap == null) {
			nameMap = HashMap(set.getClassesCount())
			set.addToMap(checkNotNull(nameMap))
		} else {
			throw JadxRuntimeException("Classpath already loaded")
		}
	}

	fun addApp(classes: List<ClassNode>) {
		if (nameMap == null) {
			nameMap = HashMap(classes.size)
		}
		for (cls in classes) {
			addClass(cls)
		}
	}

	fun initCache() {
		fillSuperTypesCache()
		fillImplementsCache()
	}

	fun isClsKnown(fullName: String): Boolean = checkNotNull(nameMap).containsKey(fullName)

	fun getClsDetails(type: ArgType): ClspClass? = checkNotNull(nameMap)[type.getObject()]

	@Nullable
	fun getMethodDetails(methodInfo: MethodInfo): IMethodDetails? {
		val cls = checkNotNull(nameMap)[methodInfo.declClass.rawName] ?: return null
		val clspMethod = getMethodFromClass(cls, methodInfo)
		if (clspMethod != null) {
			return clspMethod
		}
		// 当前类没有，则沿父类继续深搜
		for (parent in cls.parents.orEmpty()) {
			val clspParent = getClspClass(checkNotNull(parent))
			if (clspParent != null) {
				val methodFromParent = getMethodFromClass(clspParent, methodInfo)
				if (methodFromParent != null) {
					return methodFromParent
				}
			}
		}
		// 完全未知的方法，返回简化详情兜底
		return SimpleMethodDetails(methodInfo)
	}

	private fun getMethodFromClass(cls: ClspClass, methodInfo: MethodInfo): ClspMethod? = cls.methodsMap[methodInfo.shortId]

	private fun addClass(cls: ClassNode) {
		val clsType = cls.classInfo.type
		val rawName = clsType.getObject()
		val clspClass = ClspClass(clsType, -1, cls.accessFlags.rawValue(), ClspClassSource.APP)
		clspClass.parents = ClsSet.makeParentsArray(cls)
		checkNotNull(nameMap)[rawName] = clspClass
	}

	/** @return [clsName] 是否为 [implClsName] 的子类型（含接口实现） */
	fun isImplements(clsName: String, implClsName: String): Boolean = getSuperTypes(clsName).contains(implClsName)

	fun getImplementations(clsName: String): List<String> {
		val list = checkNotNull(implementsCache)[clsName]
		return list ?: emptyList()
	}

	private fun fillImplementsCache() {
		val map = HashMap<String, MutableList<String>>(checkNotNull(nameMap).size)
		val classes = ArrayList(checkNotNull(nameMap).keys)
		classes.sort()
		for (cls in classes) {
			for (st in getSuperTypes(cls)) {
				map.computeIfAbsent(st) { ArrayList() }.add(cls)
			}
		}
		implementsCache = map
	}

	fun getCommonAncestor(clsName: String, implClsName: String): String? {
		if (clsName == implClsName) {
			return clsName
		}
		val cls = checkNotNull(nameMap)[implClsName]
		if (cls == null) {
			missingClasses.add(clsName)
			return null
		}
		if (isImplements(clsName, implClsName)) {
			return implClsName
		}
		val anc = getSuperTypes(clsName)
		return searchCommonParent(anc, cls)
	}

	private fun searchCommonParent(anc: Set<String>, cls: ClspClass): String? {
		for (p in cls.parents.orEmpty()) {
			val parent = checkNotNull(p)
			val name = parent.getObject()
			if (anc.contains(name)) {
				return name
			}
			val nCls = getClspClass(parent)
			if (nCls != null) {
				val r = searchCommonParent(anc, nCls)
				if (r != null) {
					return r
				}
			}
		}
		return null
	}

	fun getSuperTypes(clsName: String): Set<String> {
		val result = checkNotNull(superTypesCache)[clsName]
		return result ?: emptySet()
	}

	private fun fillSuperTypesCache() {
		val map = HashMap<String, Set<String>>(checkNotNull(nameMap).size)
		val tmpSet = HashSet<String>()
		for (entry in checkNotNull(nameMap).entries) {
			val cls = entry.value
			tmpSet.clear()
			addSuperTypes(cls, tmpSet)
			val result: Set<String> = when (tmpSet.size) {
				0 -> emptySet()

				1 -> {
					val supCls = tmpSet.iterator().next()
					if (supCls == Consts.CLASS_OBJECT) {
						OBJECT_SINGLE_SET
					} else {
						Collections.singleton(supCls)
					}
				}

				else -> HashSet(tmpSet)
			}
			map[cls.getName()] = result
		}
		superTypesCache = map
	}

	private fun addSuperTypes(cls: ClspClass, result: MutableSet<String>) {
		for (parentType in cls.parents.orEmpty()) {
			if (parentType == null) {
				continue
			}
			val parentCls = getClspClass(parentType)
			if (parentCls != null) {
				val isNew = result.add(parentCls.getName())
				if (isNew) {
					addSuperTypes(parentCls, result)
				}
			} else {
				// 父类型未知：直接记录原始名字
				result.add(parentType.getObject())
			}
		}
	}

	@Nullable
	private fun getClspClass(clsType: ArgType): ClspClass? {
		val clspClass = checkNotNull(nameMap)[clsType.getObject()]
		if (clspClass == null) {
			missingClasses.add(clsType.getObject())
		}
		return clspClass
	}

	fun printMissingClasses() {
		val count = missingClasses.size
		if (count == 0) {
			return
		}
		LOG.warn("Found {} references to unknown classes", count)
		if (LOG.isDebugEnabled) {
			val clsNames = ArrayList(missingClasses)
			clsNames.sort()
			for (cls in clsNames) {
				LOG.debug("  {}", cls)
			}
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ClspGraph::class.java)

		/** java.lang.Object 单元素集合的共享实例，避免重复创建 */
		private val OBJECT_SINGLE_SET: Set<String> = Collections.singleton(Consts.CLASS_OBJECT)
	}
}
