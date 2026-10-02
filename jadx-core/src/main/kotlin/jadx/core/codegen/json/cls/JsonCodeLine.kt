package jadx.core.codegen.json.cls

/**
 * 方法体内单行代码的 JSON DTO：代码文本、可选字节码偏移、可选源码行号。
 */
class JsonCodeLine {
	var code: String? = null
	var offset: String? = null

	/** 可能为 null（无调试信息时），故标为可空。 */
	var sourceLine: Int? = null
}
