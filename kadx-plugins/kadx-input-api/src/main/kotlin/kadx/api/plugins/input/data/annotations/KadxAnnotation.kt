package kadx.api.plugins.input.data.annotations

/**
 * KADX 注解实现类。
 *
 * [IAnnotation] 接口的默认实现，用于存储和表示注解数据。
 *
 * **使用场景**：
 * 1. 插件从 Dex/Class 解析出注解后创建此对象
 * 2. 测试代码构造模拟注解数据
 * 3. 反编译器内部传递注解信息
 *
 * **不可变性**：所有属性都是 final，线程安全，可安全共享。
 *
 * @param visibility 注解可见性（BUILD/RUNTIME/SYSTEM），可为 null
 * @param type 注解类型的完整类名
 * @param values 注解属性值映射表
 *
 * **创建示例**：
 * ```kotlin
 * val annotation = KadxAnnotation(
 *     visibility = AnnotationVisibility.RUNTIME,
 *     type = "org.junit.Test",
 *     values = mapOf("timeout" to EncodedValue(EncodedType.INT, 5000))
 * )
 * // 表示 @Test(timeout = 5000)
 * ```
 */
public class KadxAnnotation(
	/**
	 * 注解可见性（同时实现 [IAnnotation.visibility]）。
	 *
	 * **可空**：Dex 嵌套注解等场景下输入不携带可见性信息，原 Java 字段可为 null，
	 * 这里保持同样的运行时行为（构造时不做非空检查）。
	 */
	override val visibility: AnnotationVisibility?,
	/** 注解类型的完整类名（如 "java.lang.Override"），对外通过 [annotationClass] 暴露 */
	private val type: String,
	/** 注解属性值映射（属性名 → EncodedValue，同时实现 [IAnnotation.values]）*/
	override val values: Map<String, EncodedValue>,
) : IAnnotation {

	/**
	 * 返回注解类型的完整类名。
	 *
	 * @return 如 "java.lang.Override"、"org.junit.Test"
	 */
	override val annotationClass: String get() = type

	/**
	 * 调试友好的字符串表示。
	 *
	 * **输出示例**：
	 * `Annotation{RUNTIME, type=org.junit.Test, values={timeout=EncodedValue{INT: 5000}}}`
	 */
	override fun toString(): String = "Annotation{$visibility, type=$type, values=$values}"
}
