package jadx.core.codegen.json.cls

import com.google.gson.annotations.SerializedName

/**
 * 类级 JSON DTO：一个被反编译类的结构化描述（包名、类型、父类、接口、字段、方法、内部类等）。
 *
 * **Gson 说明**：字段名默认按 Gson 的命名策略序列化；
 * 个别字段用 `@SerializedName` 指定对外 JSON key（如 Java 关键字 `package`、`extends`、`implements`）。
 */
class JsonClass : JsonNode() {
	@SerializedName("package")
	var pkg: String? = null

	/** class / interface / enum */
	var type: String? = null

	@SerializedName("extends")
	var superClass: String? = null

	@SerializedName("implements")
	var interfaces: List<String>? = null
	var dex: String? = null

	var fields: List<JsonField>? = null
	var methods: List<JsonMethod>? = null
	var innerClasses: List<JsonClass>? = null

	var imports: List<String>? = null
}
