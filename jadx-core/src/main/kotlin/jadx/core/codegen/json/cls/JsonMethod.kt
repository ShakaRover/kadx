package jadx.core.codegen.json.cls

/**
 * 方法级 JSON DTO：签名、返回类型、参数、方法体代码行与字节码偏移。
 */
class JsonMethod : JsonNode() {
	var signature: String? = null
	var returnType: String? = null
	var arguments: List<String>? = null
	var lines: List<JsonCodeLine>? = null
	var offset: String? = null
}
