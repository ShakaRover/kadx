package jadx.api.plugins.input.data.attributes.types

import jadx.api.plugins.input.data.annotations.AnnotationVisibility
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.PinnedAttribute

/**
 * 注解列表属性：存储一个节点（类/方法/字段）上的所有注解。
 *
 * **背景**：这是反编译器中最核心的属性之一，class file / Dex 解析出的
 * 运行时、类级、方法级注解最终都汇聚到这里。key 为注解类型的完整类名，
 * value 为 [IAnnotation] 实例（同名注解后者覆盖前者）。
 *
 * @param map 注解类型全名 → [IAnnotation] 的映射
 *
 * **Kotlin 转换说明**：
 * - 原 Java 的 `map` 字段是私有的且没有 getter，这里保持私有构造器参数（不生成 getMap()）；
 * - 静态工厂方法 [pack] 放入 companion object 并加 @JvmStatic，Java 调用方仍写 `AnnotationsAttr.pack(...)`；
 * - `getList()` 转成属性 `val list`（字节码 getter 同名），其余非 getter 方法保持函数形式。
 */
public class AnnotationsAttr(
	/** 注解类型全名 → [IAnnotation] 的映射（私有，与原 Java 一致不暴露 getter）*/
	private val map: Map<String, IAnnotation>,
) : PinnedAttribute() {

	companion object {
		/**
		 * 从注解列表构建属性：过滤掉 SYSTEM 可见性注解后打包。
		 *
		 * **可空语义**（与原 Java @Nullable 一致）：输入为空、或过滤后无剩余注解时返回 null，
		 * 调用方需要判空。
		 *
		 * @param annotationList 解析出的注解列表
		 */
		@JvmStatic
		public fun pack(annotationList: List<IAnnotation>): AnnotationsAttr? {
			if (annotationList.isEmpty()) {
				return null
			}
			val annMap = HashMap<String, IAnnotation>(annotationList.size)
			for (ann in annotationList) {
				if (ann.visibility != AnnotationVisibility.SYSTEM) {
					annMap[ann.annotationClass] = ann
				}
			}
			if (annMap.isEmpty()) {
				return null
			}
			return AnnotationsAttr(annMap)
		}
	}

	/**
	 * 按注解类型全名查找单个注解。
	 *
	 * @return 对应注解，不存在时为 null（与原 Java @Nullable 一致）
	 */
	public fun get(className: String): IAnnotation? = map[className]

	/** 返回所有注解的集合视图（map.values()，与原 Java 一致）*/
	public val all: Collection<IAnnotation> get() = map.values

	/**
	 * 返回注解列表副本。
	 *
	 * 空映射时返回 [emptyList]，否则返回 values 的新 ArrayList —— 与原 Java 行为完全一致。
	 */
	public val list: List<IAnnotation> get() = if (map.isEmpty()) emptyList() else ArrayList(map.values)

	/** 注解数量（注意：这是普通方法而非 getter，Kotlin 中仍写作 size()）*/
	public fun size(): Int = map.size

	/** 是否为空（同上，普通方法）*/
	public val isEmpty: Boolean get() = map.isEmpty()

	/**
	 * 返回本属性的类型标识：[JadxAttrType.ANNOTATION_LIST]。
	 *
	 * 原 Java 声明为协变的具体类型，这里保持一致。
	 */
	override val attrType: JadxAttrType<AnnotationsAttr> get() = JadxAttrType.ANNOTATION_LIST

	/** 调试字符串：直接输出内部映射（与原 Java `map.toString()` 一致）*/
	override fun toString(): String = map.toString()
}
