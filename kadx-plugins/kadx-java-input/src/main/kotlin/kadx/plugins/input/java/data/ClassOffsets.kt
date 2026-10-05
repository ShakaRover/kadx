package kadx.plugins.input.java.data

/**
 * .class 文件各 section 的偏移量索引。
 *
 * **做什么**：构造时一次性扫描 class 文件，记录常量池每个条目的起始偏移、
 * access flags / 类类型 / 父类 / 接口表 / 字段表 / 方法表 / attributes 区的关键位置，
 * 之后解析器可以按编号随机访问任意常量池条目而无需重新遍历。
 *
 * **为什么在构造时扫描**：class 文件是线性格式，字段/方法/属性区没有目录；
 * 提前算好偏移能把后续多次读取从 O(n) 降为 O(1)。
 */
class ClassOffsets(data: DataReader) {

	// 注意：以下属性初始化器按声明顺序执行，对 [data] 的副作用（offset 推进）依赖这个顺序，勿随意调换
	private val constPoolOffsets: IntArray = readConstPool(data)
	private val constPoolEnd: Int = data.offset
	private val interfacesEnd: Int = run {
		val interfacesCount = data.absPos(constPoolEnd + 6).readU2()
		data.skip(interfacesCount * 2)
		data.offset
	}
	private val attributesOff: Int = run {
		skipFields(data)
		skipMethods(data)
		data.offset
	}

	/** @return 第 [num] 个常量池条目的起始偏移（下标从 1 开始，0 位未用） */
	fun getOffsetOfConstEntry(num: Int): Int = constPoolOffsets[num]

	// 以下均为"常量池结束位置 + 固定偏移"推导出的 section 起点（对应原 Java getter，属性化后 JVM 方法名不变）
	val accessFlagsOffset: Int get() = constPoolEnd
	val clsTypeOffset: Int get() = constPoolEnd + 2
	val superTypeOffset: Int get() = constPoolEnd + 4
	val interfacesOffset: Int get() = constPoolEnd + 6
	val fieldsOffset: Int get() = interfacesEnd
	val attributesOffset: Int get() = attributesOff

	companion object {
		/**
		 * 遍历常量池，记录每个条目的偏移。
		 * LONG/DOUBLE 占两个条目槽位（JVM 规范规定其后一位不可用），需额外跳一格。
		 */
		private fun readConstPool(data: DataReader): IntArray {
			val cpSize = data.absPos(8).readU2()
			val cpOffsets = IntArray(cpSize + 1)
			var i = 1
			while (i < cpSize) {
				val tag = data.readU1()
				cpOffsets[i] = data.offset
				when (val constType = ConstantType.getTypeByTag(tag)) {
					ConstantType.UTF8 -> data.skip(data.readU2())

					ConstantType.LONG, ConstantType.DOUBLE -> {
						data.skip(8)
						i++ // 双字条目占用下一槽位
					}

					else -> data.skip(constType.dataSize)
				}
				i++
			}
			return cpOffsets
		}
	}

	private fun skipFields(data: DataReader) {
		val fieldsCount = data.readU2()
		for (i in 0 until fieldsCount) {
			data.skip(6) // access_flags + name_index + descriptor_index
			skipAttributes(data)
		}
	}

	private fun skipMethods(data: DataReader) {
		val methodsCount = data.readU2()
		for (i in 0 until methodsCount) {
			data.skip(6) // access_flags + name_index + descriptor_index
			skipAttributes(data)
		}
	}

	private fun skipAttributes(data: DataReader) {
		val attrCount = data.readU2()
		for (i in 0 until attrCount) {
			data.skip(2) // attribute_name_index
			val len = data.readU4()
			data.skip(len)
		}
	}
}
