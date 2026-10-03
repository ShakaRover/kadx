package jadx.api.plugins.input.data.attributes.types

import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.PinnedAttribute

/**
 * 注解方法参数属性：存储方法每个形参上的注解列表（按参数位置索引）。
 *
 * **背景**：对应 Java 源码中给形参加注解的写法，如
 * ```java
 * void process(@NotNull String name, @Nullable int code) { ... }
 * ```
 * `paramList` 的第 i 个元素是第 i 个参数的 [AnnotationsAttr]。
 *
 * **构造限制**：与原 Java 一致，构造器为 private，只能通过静态工厂方法 [pack] 创建。
 *
 * @param paramList 参数注解列表（下标 = 参数位置）
 */
public class AnnotationMethodParamsAttr private constructor(
	/**
	 * 参数注解列表（下标 = 参数位置）。
	 *
	 * **元素可空**：某参数没有非 SYSTEM 注解时，[pack] 会在对应下标存入 null
	 * （与原 Java 行为一致，调用方如 AnnotationGen/UsageInfoVisitor 都会判空）。
	 */
	public val paramList: List<AnnotationsAttr?>,
) : PinnedAttribute() {

	companion object {
		/**
		 * 从"每个参数的注解列表"构建属性。
		 *
		 * **可空语义**（与原 Java @Nullable 一致）：输入为空时返回 null，调用方需要判空。
		 *
		 * @param annotationRefList 外层下标 = 参数位置，内层为该参数的注解列表
		 */
		@JvmStatic
		public fun pack(annotationRefList: List<List<IAnnotation>>): AnnotationMethodParamsAttr? {
			if (annotationRefList.isEmpty()) {
				return null
			}
			// 元素可空：pack() 可能返回 null（与原 Java 行为一致）
			val list = ArrayList<AnnotationsAttr?>(annotationRefList.size)
			for (annList in annotationRefList) {
				list.add(AnnotationsAttr.pack(annList))
			}
			return AnnotationMethodParamsAttr(list)
		}
	}

	/**
	 * 返回本属性的类型标识：[JadxAttrType.ANNOTATION_MTH_PARAMETERS]。
	 *
	 * 原 Java 声明为协变的具体类型，这里保持一致。
	 */
	override val attrType: JadxAttrType<AnnotationMethodParamsAttr> get() = JadxAttrType.ANNOTATION_MTH_PARAMETERS

	/** 调试字符串：直接输出参数列表（与原 Java `paramList.toString()` 一致）*/
	override fun toString(): String = paramList.toString()
}
