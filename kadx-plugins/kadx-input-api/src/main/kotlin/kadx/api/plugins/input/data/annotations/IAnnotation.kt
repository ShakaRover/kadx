package kadx.api.plugins.input.data.annotations

/**
 * 注解数据接口。
 *
 * 表示一个 Java/Dex 注解实例，包含注解类型、可见性和属性值。
 *
 * **注解结构示例**：
 * ```java
 * @MyAnnotation(value = "hello", enabled = true)
 * ```
 * - annotationClass: "com.example.MyAnnotation"
 * - visibility: RUNTIME
 * - values: {"value" -> "hello", "enabled" -> true}
 *
 * **与 Java 反射的对应关系**：
 * | KADX | Java Reflection |
 * |------|------------------|
 * | IAnnotation | java.lang.annotation.Annotation |
 * | annotationClass | annotationType().getName() |
 * | values | 需要通过反射逐个获取 |
 *
 * **Kotlin 转换说明（重要）**：
 * 原 Java 接口声明的是 `getAnnotationClass()` / `getVisibility()` / `getValues()` /
 * `getDefaultValue()` 四个方法。Kotlin 调用方看 Java getter 时会自动合成属性
 * （`.annotationClass`、`.values` 等），因此这里直接声明为 Kotlin 属性：
 * - 字节码层面仍生成同名 getter 抽象方法，Java 调用方零改动；
 * - Kotlin 调用方可继续用属性语法（如 `ann.values["value"]`）。
 *
 * @see EncodedValue 注解属性的编码值
 */
public interface IAnnotation {

	/**
	 * 返回注解类型的完整类名。
	 *
	 * @return 如 "java.lang.Override"、"org.junit.Test"
	 */
	public val annotationClass: String

	/**
	 * 返回注解的可见性（保留策略）。
	 *
	 * **可空说明**：某些输入格式（如 Dex 中嵌套注解 ENCODED_ANNOTATION）不携带
	 * 可见性信息，此时为 null。原 Java 接口未声明 @Nullable，但运行时确实可能为 null，
	 * 这里如实标记为可空。
	 *
	 * @return 注解在哪个阶段可见，或 null
	 */
	public val visibility: AnnotationVisibility?

	/**
	 * 返回注解的所有属性值映射。
	 *
	 * @return key 为属性名，value 为 [EncodedValue] 对象
	 *
	 * **示例**：`@MyAnnotation(value = "hello", priority = 10)`
	 * - 返回 `{"value" -> EncodedValue("hello"), "priority" -> EncodedValue(10)}`
	 */
	public val values: Map<String, EncodedValue>

	/**
	 * 获取注解的默认值（如果存在）。
	 *
	 * @return "value" 属性的值，或 null
	 *
	 * **使用场景**：许多单值注解使用省略语语法
	 * ```java
	 * @Deprecated("use new method")  // 等价于 @Deprecated(value = "use new method")
	 * ```
	 *
	 * **默认行为**：从 values 中获取键为 "value" 的条目（与原 Java default 方法一致）
	 */
	public val defaultValue: EncodedValue? get() = values["value"]
}
