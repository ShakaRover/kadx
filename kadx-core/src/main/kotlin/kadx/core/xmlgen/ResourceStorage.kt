package kadx.core.xmlgen

import kadx.api.security.IKadxSecurity
import kadx.core.xmlgen.entry.ResourceEntry
import java.util.ArrayList
import java.util.HashMap
import java.util.TreeMap

/**
 * 资源条目存储：保存解析出的所有 [ResourceEntry]，并负责：
 * - 按“配置 + 类型 + 名称”去重（[uniqNameEntries]）；
 * - 按资源 id 保留跨配置的重命名（[renames]）；
 * - 生成 `id -> type/name` 映射（[resourcesNames]）供 `R` 类与反混淆使用。
 */
class ResourceStorage(private val security: IKadxSecurity) {

	private val list: MutableList<ResourceEntry> = ArrayList()

	/** 同一配置与类型下名称必须唯一。 */
	private val uniqNameEntries: MutableMap<ResourceEntry, ResourceEntry> = TreeMap(RES_ENTRY_NAME_COMPARATOR)

	/** 同一 id 在不同配置下保持同名。 */
	private val renames: MutableMap<Int, String> = HashMap()

	/** 应用包名；setter 会经过安全校验。 */
	var appPackage: String? = null
		set(value) {
			field = security.verifyAppPackage(value)
		}

	fun add(resEntry: ResourceEntry) {
		list.add(resEntry)
		uniqNameEntries[resEntry] = resEntry
	}

	fun replace(prevResEntry: ResourceEntry, newResEntry: ResourceEntry) {
		val idx = list.indexOf(prevResEntry)
		if (idx != -1) {
			list[idx] = newResEntry
		}
		// don't remove from unique names so old name stays occupied
	}

	fun addRename(entry: ResourceEntry) {
		addRename(entry.id, entry.keyName)
	}

	fun addRename(id: Int, keyName: String) {
		renames[id] = keyName
	}

	fun getRename(id: Int): String? = renames[id]

	fun searchEntryWithSameName(resourceEntry: ResourceEntry): ResourceEntry? = uniqNameEntries[resourceEntry]

	fun finish() {
		list.sortBy { it.id }
		uniqNameEntries.clear()
		renames.clear()
	}

	fun size(): Int = list.size

	/** 全部资源条目（按 id 排序）。 */
	val resources: Iterable<ResourceEntry>
		get() = list

	/** `id -> "type/name"` 映射，供 `R` 类字段生成与资源反混淆使用。 */
	val resourcesNames: Map<Int, String>
		get() {
			val map = HashMap<Int, String>()
			for (entry in list) {
				map[entry.id] = entry.typeName + '/' + entry.keyName
			}
			return map
		}

	companion object {
		/** 先按配置、再按类型、最后按名称排序，保证同名冲突可稳定检测。 */
		private val RES_ENTRY_NAME_COMPARATOR: Comparator<ResourceEntry> =
			compareBy({ it.config }, { it.typeName }, { it.keyName })
	}
}
