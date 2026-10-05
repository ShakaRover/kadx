package kadx.core.codegen.json.cls

/**
 * 字段级 JSON DTO：在 [JsonNode] 基础上额外保存字段类型字符串。
 *
 * 原 Java 中 `type` 是包级私有字段（没有 getter/setter），
 * 这里声明为普通属性（生成 getter/setter 不影响 Gson，Gson 读取的是同名字段）。
 */
class JsonField : JsonNode() {
	var type: String? = null
}
