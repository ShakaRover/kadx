package kadx.gui.cache.usage

import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.FieldNode
import kadx.core.dex.nodes.MethodNode

/**
 * 原始 usage 数据的聚合容器。
 *
 * **做什么**：按类原始名索引 [ClsUsageData]，并在需要时懒创建类 / 方法 / 字段的
 * usage 明细。它是内存缓存与磁盘缓存之间的通用数据结构。
 *
 * **为什么不用 `data class`**：内部持有可变 Map，且 [collectClassesWithoutData]
 * 会原地更新字段，属于有状态聚合器。
 */
internal class RawUsageData {

	/** 类原始名 -> 类 usage 数据。 */
	val clsMap: MutableMap<String, ClsUsageData> = HashMap()

	/** 出现在依赖 / 被使用列表中、但自身没有 usage 数据的类（排序后）。 */
	var classesWithoutData: List<String> = emptyList()

	/** 取某个类节点的 usage 数据（按原始名索引）。 */
	fun getClassData(cls: ClassNode): ClsUsageData = getClassData(cls.rawName)

	/** 取指定原始名的 usage 数据；不存在则新建。 */
	fun getClassData(clsRawName: String): ClsUsageData = clsMap.getOrPut(clsRawName) { ClsUsageData(clsRawName) }

	/** 取某个方法节点的 usage 数据（按“父类 + 方法短 id”索引）。 */
	fun getMethodData(mth: MethodNode): MthUsageData {
		val parentClass = mth.parentClass
		val shortId = mth.methodInfo.shortId
		return getClassData(parentClass).mthUsage.getOrPut(shortId) {
			MthUsageData(MthRef(parentClass.rawName, shortId))
		}
	}

	/** 取某个字段节点的 usage 数据（按“父类 + 字段短 id”索引）。 */
	fun getFieldData(fld: FieldNode): FldUsageData {
		val parentClass = fld.parentClass
		val shortId = fld.getFieldInfo().shortId
		return getClassData(parentClass).fldUsage.getOrPut(shortId) {
			FldUsageData(FldRef(parentClass.rawName, shortId))
		}
	}

	/**
	 * 汇总“只被引用、却没有自身 usage 数据”的类。
	 *
	 * **做什么**：遍历所有类的依赖与使用列表，收集全部类名，再减去已有 usage 数据的类；
	 * 结果排序后存入 [classesWithoutData]，序列化时一并写出，反序列化时用于恢复类名池。
	 */
	fun collectClassesWithoutData() {
		val allClasses = HashSet<String>(clsMap.size * 2)
		for (usageData in clsMap.values) {
			val deps = usageData.clsDeps
			if (deps != null) {
				allClasses.addAll(deps)
			}
			val usage = usageData.clsUsage
			if (usage != null) {
				allClasses.addAll(usage)
			}
		}
		allClasses.removeAll(clsMap.keys)
		val list = ArrayList(allClasses)
		list.sort()
		classesWithoutData = list
	}
}
