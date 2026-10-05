package kadx.core.codegen.json.mapping

/**
 * 类映射的 JSON DTO：记录类名 / 别名、对应 JSON 文件、是否内部类、顶层类名及成员映射。
 *
 * **Kotlin 转换说明**：原 Java 的 `isInner()` / `setInner()` 这对方法，
 * 这里用“私有属性 + 显式函数”实现，既保持 JVM 方法名完全一致，
 * 又让 Gson 读取的字段名仍为 `inner`（若写成 `var isInner`，Gson 字段名会变成 `isInner`，改变输出）。
 */
class JsonClsMapping {
	var name: String? = null
	var alias: String? = null

	var json: String? = null

	private var inner: Boolean = false
	var topClass: String? = null

	var fields: List<JsonFieldMapping>? = null
	var methods: List<JsonMthMapping>? = null

	val isInner: Boolean get() = inner

	fun setInner(inner: Boolean) {
		this.inner = inner
	}
}
