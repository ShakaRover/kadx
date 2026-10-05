package kadx.core.dex.attributes

import kadx.api.plugins.input.data.annotations.IAnnotation
import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.api.plugins.input.data.attributes.KadxAttrType
import kadx.core.utils.ListUtils
import kadx.core.utils.Utils
import kadx.core.utils.exceptions.KadxRuntimeException
import java.util.Collections
import java.util.EnumSet
import java.util.IdentityHashMap

/**
 * 属性存储：每个节点（类/方法/字段/指令/基本块等）都持有一个本对象。
 *
 * **存两类东西**：
 * 1. **标志位（Flags）**：布尔型属性，用 [EnumSet] 存放 [AFlag]，判断/增删极快；
 * 2. **属性（Attributes）**：类实例型属性，用 `IdentityHashMap` 按 [IKadxAttrType] 存放
 *    [IKadxAttribute]（每种类型只保留一个，后写入的覆盖先写入的）。
 *
 * **为什么用 IdentityHashMap？** 属性类型对象本身是单例，按引用比较即可，比普通 HashMap
 * 更省（不做 equals/hashCode 计算）。
 *
 * **延迟创建**：绝大多数节点没有任何属性，因此初始共享只读空表 [EMPTY_ATTRIBUTES]，
 * 真正写入时才替换为 [IdentityHashMap]，清空后又换回空表，避免大量空对象。
 *
 * **线程安全**：所有写操作通过 [writeAttributes] 在 `synchronized(this)` 中执行。
 */
open class AttributeStorage {

	companion object {
		init {
			// 保证 AFlag 常量数不超过 64，这样 EnumSet 才能用一个 long 表示（性能优化前提）
			val flagsCount = AFlag.values().size
			if (flagsCount >= 64) {
				throw KadxRuntimeException(
					"Try to reduce flags count to 64 for use one long in EnumSet, now $flagsCount",
				)
			}
		}

		/** 共享的只读空属性表（用引用相等 `===` 判断当前是否处于“空”状态） */
		private val EMPTY_ATTRIBUTES: MutableMap<IKadxAttrType<*>, IKadxAttribute> = Collections.emptyMap()

		/** 从属性列表构造一个存储（Java 调用方写 `AttributeStorage.fromList(...)`） */
		fun fromList(list: List<IKadxAttribute>): AttributeStorage {
			val storage = AttributeStorage()
			storage.add(list)
			return storage
		}
	}

	private val flags: MutableSet<AFlag> = EnumSet.noneOf(AFlag::class.java)
	private var attributes: MutableMap<IKadxAttrType<*>, IKadxAttribute> = EMPTY_ATTRIBUTES

	open fun add(flag: AFlag) {
		flags.add(flag)
	}

	open fun add(attr: IKadxAttribute) {
		writeAttributes { map -> map[attr.attrType] = attr }
	}

	open fun add(list: List<IKadxAttribute>) {
		writeAttributes { map -> list.forEach { attr -> map[attr.attrType] = attr } }
	}

	/** 向列表型属性追加一个元素（列表不存在时先创建） */
	open fun <T> add(type: IKadxAttrType<AttrList<T>>, obj: T) {
		val list = get(type)
		if (list != null) {
			list.list.add(obj)
		} else {
			add(AttrList(type, ListUtils.mutableListOf(obj)))
		}
	}

	/** 向列表型属性批量追加元素（列表不存在时先创建） */
	open fun <T> addAttrList(type: IKadxAttrType<AttrList<T>>, attrList: List<T>) {
		val list = get(type)
		if (list != null) {
			list.list.addAll(attrList)
		} else {
			add(AttrList(type, attrList))
		}
	}

	open fun addAll(otherList: AttributeStorage) {
		flags.addAll(otherList.flags)
		if (otherList.attributes.isNotEmpty()) {
			writeAttributes { m -> m.putAll(otherList.attributes) }
		}
	}

	open operator fun contains(flag: AFlag): Boolean = flags.contains(flag)

	open operator fun <T : IKadxAttribute> contains(type: IKadxAttrType<T>): Boolean = attributes.containsKey(type)

	/** 按类型取属性，不存在返回 null（原 Java 语义允许 null） */
	@Suppress("UNCHECKED_CAST")
	open fun <T : IKadxAttribute> get(type: IKadxAttrType<T>): T? = attributes[type] as T?

	open fun getAnnotation(cls: String): IAnnotation? {
		val aList = get(KadxAttrType.ANNOTATION_LIST)
		return aList?.get(cls)
	}

	open fun <T> getAll(type: IKadxAttrType<AttrList<T>>): List<T> {
		val attrList = get(type) ?: return Collections.emptyList()
		return Collections.unmodifiableList(attrList.list)
	}

	open fun remove(flag: AFlag) {
		flags.remove(flag)
	}

	open fun clearFlags() {
		flags.clear()
	}

	open fun <T : IKadxAttribute> remove(type: IKadxAttrType<T>) {
		if (attributes.isNotEmpty()) {
			writeAttributes { map -> map.remove(type) }
		}
	}

	open fun remove(attr: IKadxAttribute) {
		if (attributes.isNotEmpty()) {
			writeAttributes { map ->
				val type = attr.attrType
				val a = map[type]
				// 只有同一个对象实例才移除（引用比较，对应 Java 的 `==`）
				if (a === attr) {
					map.remove(type)
				}
			}
		}
	}

	private fun writeAttributes(mapConsumer: (MutableMap<IKadxAttrType<*>, IKadxAttribute>) -> Unit) {
		synchronized(this) {
			if (attributes === EMPTY_ATTRIBUTES) {
				attributes = IdentityHashMap(2) // 大多数情况只会添加 1~2 个属性
			}
			mapConsumer(attributes)
			if (attributes.isEmpty()) {
				attributes = EMPTY_ATTRIBUTES
			}
		}
	}

	/** 卸载不需要常驻的属性（保留 [IKadxAttribute.keepLoaded] 为 true 的属性） */
	open fun unloadAttributes() {
		if (attributes.isEmpty()) {
			return
		}
		writeAttributes { map -> map.entries.removeAll { !it.value.keepLoaded() } }
	}

	open fun getAttributeStrings(): List<String> {
		val size = flags.size + attributes.size
		if (size == 0) {
			return Collections.emptyList()
		}
		val list = ArrayList<String>(size)
		for (a in flags) {
			list.add(a.toString())
		}
		for (a in attributes.values) {
			list.add(a.toAttrString())
		}
		return list
	}

	open fun isEmpty(): Boolean = flags.isEmpty() && attributes.isEmpty()

	override fun toString(): String {
		val list = getAttributeStrings()
		if (list.isEmpty()) {
			return ""
		}
		val sorted = list.sorted()
		return "A[" + Utils.listToString(sorted) + ']'
	}
}
