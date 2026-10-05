package kadx.core.codegen.json.cls

/**
 * JSON 输出 DTO 的基类：保存类 / 字段 / 方法共有的名字、别名、声明文本与访问标志。
 *
 * **为什么用普通 class 而不是 data class**：
 * 原 Java 未覆写 equals/hashCode，这些对象仅作为 Gson 序列化的数据载体，
 * 保持身份语义（identity）即可；data class 会自动生成 equals/hashCode/toString，
 * 与原始行为不一致，因此不使用。
 *
 * **Kotlin 转换说明**：原 Java 是私有字段 + 公开 getter/setter，
 * 这里直接声明为 `var` 属性，生成的 JVM getter/setter 名称完全一致，
 * 字段名也不变，Gson 反射读取字段的行为不变。
 */
open class JsonNode {
	var name: String? = null
	var alias: String? = null
	var declaration: String? = null
	var accessFlags: Int = 0
}
