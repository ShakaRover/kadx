package jadx.plugins.input.java.data.attributes.types

import jadx.api.plugins.input.data.annotations.AnnotationVisibility
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr
import jadx.api.plugins.utils.Utils
import jadx.plugins.input.java.data.DataReader
import jadx.plugins.input.java.data.JavaClassData
import jadx.plugins.input.java.data.attributes.IJavaAttribute
import jadx.plugins.input.java.data.attributes.IJavaAttributeReader
import jadx.plugins.input.java.data.attributes.JavaAttrStorage
import jadx.plugins.input.java.data.attributes.JavaAttrType
import java.util.ArrayList
import java.util.Collections

/**
 * RuntimeVisible/InvisibleParameterAnnotations attribute：方法参数上的注解。
 *
 **做什么**：按"参数个数 + 每参数的注解列表"结构解析，得到二维表 list[参数][注解]；
 * [merge] 把运行时可见与不可见两组按参数位置合并回通用 [AnnotationMethodParamsAttr]。
 */
class JavaParamAnnsAttr(
	/** 每个参数对应的注解列表（下标 = 参数序号） */
	val list: List<List<IAnnotation>>,
) : IJavaAttribute {

	companion object {
		/** @return 指定可见性级别的参数注解读取器 */
		fun reader(visibility: AnnotationVisibility): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute {
				val len = reader.readU1()
				val list = ArrayList<List<IAnnotation>>(len)
				for (i in 0 until len) {
					list.add(JavaAnnotationsAttr.readAnnotationsList(visibility, clsData, reader))
				}
				return JavaParamAnnsAttr(list)
			}
		}

		/** 把存储中的两组参数注解按位置合并；都为空时返回 null */
		fun merge(storage: JavaAttrStorage): AnnotationMethodParamsAttr? {
			val runtimeAnnAttr = storage.get(JavaAttrType.RUNTIME_PARAMETER_ANNOTATIONS)
			val buildAnnAttr = storage.get(JavaAttrType.BUILD_PARAMETER_ANNOTATIONS)
			if (runtimeAnnAttr == null) {
				// 两组都为空 → 无注解可合并
				if (buildAnnAttr == null) {
					return null
				}
				return AnnotationMethodParamsAttr.pack(buildAnnAttr.list)
			}
			if (buildAnnAttr == null) {
				return AnnotationMethodParamsAttr.pack(runtimeAnnAttr.list)
			}
			return AnnotationMethodParamsAttr.pack(mergeParamLists(runtimeAnnAttr.list, buildAnnAttr.list))
		}

		private fun mergeParamLists(first: List<List<IAnnotation>>, second: List<List<IAnnotation>>): List<List<IAnnotation>> {
			val firstSize = first.size
			val secondSize = second.size
			val size = maxOf(firstSize, secondSize)
			val result = ArrayList<List<IAnnotation>>(size)
			for (i in 0 until size) {
				val firstList = if (i < firstSize) first[i] else Collections.emptyList()
				val secondList = if (i < secondSize) second[i] else Collections.emptyList()
				result.add(Utils.concat(firstList, secondList))
			}
			return result
		}
	}
}
