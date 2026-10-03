package jadx.plugins.input.java.data.attributes.types

import jadx.api.plugins.input.data.annotations.AnnotationVisibility
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.annotations.JadxAnnotation
import jadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import jadx.api.plugins.utils.Utils
import jadx.plugins.input.java.data.ConstPoolReader
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.EncodedValueReader
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader
import jadx.plugins.input.java.data.attributes.JavaAttrStorage
import jadx.plugins.input.java.data.attributes.JavaAttrType
import java.util.ArrayList
import java.util.LinkedHashMap

/**
 * RuntimeVisible/InvisibleAnnotations attribute：类、方法、字段上的注解列表。
 *
 **做什么**：把"数量 + N 个(类型索引, 键值对数 + 键值对)"序列解析成 [IAnnotation] 列表；
 * 同时提供 [merge] 把运行时可见与不可见两组注解合并回通用 [AnnotationsAttr]。
 *
 **为什么保留插入顺序**：注解输出顺序影响可读性，用 LinkedHashMap 保持 class 文件中的原始次序。
 */
class JavaAnnotationsAttr(
	/** 解析出的注解列表（保持 class 文件中的顺序） */
	val list: List<IAnnotation>,
) : IJavaAttribute {

	companion object {
		/** @return 指定可见性级别的注解列表读取器 */
		fun reader(visibility: AnnotationVisibility): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute = JavaAnnotationsAttr(readAnnotationsList(visibility, clsData, reader))
		}

		/** 读一个注解列表（u2 数量 + N 个注解） */
		fun readAnnotationsList(visibility: AnnotationVisibility, clsData: JavaClassData, reader: DataReader): List<IAnnotation> {
			val len = reader.readU2()
			val list = ArrayList<IAnnotation>(len)
			for (i in 0 until len) {
				list.add(readAnnotation(visibility, clsData, reader))
			}
			return list
		}

		/**
		 * 读单个注解：类型索引 + "键名 → 元素值"对。
		 * 嵌套注解（'@' tag）由 [EncodedValueReader] 递归处理。
		 */
		fun readAnnotation(visibility: AnnotationVisibility, clsData: JavaClassData, reader: DataReader): JadxAnnotation {
			val constPool: ConstPoolReader = clsData.constPoolReader
			// getUtf8 可返回 null（损坏 class）；JadxAnnotation.type 声明非空，提前调用时原 Java 同样 NPE
			val type = constPool.getUtf8(reader.readU2()) ?: throw NullPointerException("annotation type is null")
			val pairsCount = reader.readU2()
			val pairs = LinkedHashMap<String?, EncodedValue>(pairsCount)
			for (j in 0 until pairsCount) {
				val name = constPool.getUtf8(reader.readU2())
				val value = EncodedValueReader.read(clsData, reader)
				// 原 Java HashMap 允许 null 键；Kotlin map 索引操作符不接受可空键，用显式 put
				pairs.put(name, value)
			}
			// JadxAnnotation 声明 Map<String, _>，但键运行时可为 null（原 Java HashMap 同样允许）
			@Suppress("UNCHECKED_CAST")
			return JadxAnnotation(visibility, type, pairs as Map<String, EncodedValue>)
		}

		/**
		 * 把存储中的运行时/构建期两组注解合并为一个 [AnnotationsAttr]；
		 * 两组都为空时返回 null。
		 */
		fun merge(storage: JavaAttrStorage): AnnotationsAttr? {
			val runtimeAnnAttr = storage.get(JavaAttrType.RUNTIME_ANNOTATIONS)
			val buildAnnAttr = storage.get(JavaAttrType.BUILD_ANNOTATIONS)
			if (runtimeAnnAttr == null) {
				// 两组都为空 → 无注解可合并
				if (buildAnnAttr == null) {
					return null
				}
				return AnnotationsAttr.pack(buildAnnAttr.list)
			}
			if (buildAnnAttr == null) {
				return AnnotationsAttr.pack(runtimeAnnAttr.list)
			}
			return AnnotationsAttr.pack(Utils.concat(runtimeAnnAttr.list, buildAnnAttr.list))
		}
	}
}
