package jadx.plugins.input.java.data.attributes

import jadx.plugins.input.java.data.ConstPoolReader
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import org.jetbrains.annotations.Nullable
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.HashMap

/**
 * attribute 区的批量读取器。
 *
 * **做什么**：从"属性数量 + (名字索引, 长度, 数据)*"的序列中逐个解析属性，
 * 按 [JavaAttrType] 查表找到对应读取器并写入 [JavaAttrStorage]；
 * 单个属性解析失败只记日志不中断（尽力反编译原则），并用 finally 把读头跳回段尾保证后续对齐。
 */
class AttributesReader(
	private val clsData: JavaClassData,
	private val constPool: ConstPoolReader,
) {

	// nameIdx → attrType 的缓存：同一个属性名在多个方法上重复出现，避免反复查表/告警
	private val attrCache: HashMap<Int, JavaAttrType<*>> = HashMap(JavaAttrType.size())

	/** 解析当前节点的全部 attribute */
	fun loadAll(reader: DataReader): JavaAttrStorage = loadAttributes(reader) { true }

	/** 只解析 [types] 中列出的属性（其余跳过，节省时间） */
	fun loadMulti(reader: DataReader, types: Set<JavaAttrType<*>>): JavaAttrStorage = loadAttributes(reader) { type -> types.contains(type) }

	/**
	 * Load attributes into storage.
	 *
	 * @param reader 读取器位置应已定位到 attribute 区起点
	 * @param condition 判断某属性是否应该被解析并加入存储
	 */
	private fun loadAttributes(reader: DataReader, condition: (JavaAttrType<*>) -> Boolean): JavaAttrStorage {
		val count = reader.readU2()
		if (count == 0) {
			return JavaAttrStorage.EMPTY
		}
		val storage = JavaAttrStorage()
		for (i in 0 until count) {
			val nameIdx = reader.readU2()
			val len = reader.readU4()
			val end = reader.offset + len
			try {
				val attrType = resolveAttrReader(nameIdx)
				if (attrType != null && condition(attrType)) {
					val attrReader = attrType.reader
					if (attrReader != null) {
						val attrValue = attrReader.read(clsData, reader)
						if (attrValue != null) {
							storage.add(attrType, attrValue)
						}
					}
				}
			} catch (e: Exception) {
				LOG.error("Failed to parse attribute: {}", constPool.getUtf8(nameIdx), e)
			} finally {
				reader.absPos(end)
			}
		}
		return storage
	}

	/** 只查找并解析 [type] 这一种属性，找不到返回 null */
	@Nullable
	fun <T : IJavaAttribute> loadOne(reader: DataReader, type: JavaAttrType<T>): T? {
		val count = reader.readU2()
		for (i in 0 until count) {
			val nameIdx = reader.readU2()
			val len = reader.readU4()
			val end = reader.offset + len
			try {
				val attrType = resolveAttrReader(nameIdx)
				if (attrType == type) {
					// 原 Java 直接解引用 getReader()，reader 为 null 时同样 NPE
					return attrType.reader!!.read(clsData, reader) as T?
				}
			} catch (e: Exception) {
				LOG.error("Failed to parse attribute: {}", constPool.getUtf8(nameIdx), e)
			} finally {
				reader.absPos(end)
			}
		}
		return null
	}

	private fun resolveAttrReader(nameIdx: Int): JavaAttrType<*>? {
		// 等价于原 Java computeIfAbsent：查到未知名字时不缓存（下次仍会重新告警），
		// 因为 Kotlin 侧 Function 返回类型非空，无法直接传可空 lambda
		val cached = attrCache[nameIdx]
		if (cached != null) {
			return cached
		}
		val attrName = constPool.getUtf8(nameIdx)
		val attrType = JavaAttrType.byName(attrName)
		if (attrType == null) {
			LOG.warn("Unknown java class attribute type: {}", attrName)
			return null
		}
		attrCache[nameIdx] = attrType
		return attrType
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(AttributesReader::class.java)
	}
}
