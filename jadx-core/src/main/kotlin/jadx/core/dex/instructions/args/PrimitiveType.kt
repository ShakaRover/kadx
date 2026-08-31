package jadx.core.dex.instructions.args

/**
 * DEX 指令中的基本类型枚举。
 *
 * 对应 DEX 文件格式中的类型签名字符（如 'I' 表示 int，'L' 表示对象）。
 * 每个基本类型都有：
 * - shortName: DEX 二进制格式中的单字符编码
 * - longName: Java 源码中的人类可读名称
 * - boxType: 对应的包装类类型（用于自动装箱）
 */
enum class PrimitiveType(
	/** DEX 类型签名字符，如 'I'、'Z'、'L' 等 */
	val shortName: String,
	/** Java 源码中的类型名称，如 "int"、"boolean" 等 */
	val longName: String,
) {
	BOOLEAN("Z", "boolean"),
	CHAR("C", "char"),
	BYTE("B", "byte"),
	SHORT("S", "short"),
	INT("I", "int"),
	FLOAT("F", "float"),
	LONG("J", "long"),
	DOUBLE("D", "double"),
	OBJECT("L", "OBJECT"),
	ARRAY("[", "ARRAY"),
	VOID("V", "void"),
	;

	/**
	 * 对应的包装类 ArgType（用于自动装箱场景）。
	 */
	fun getBoxType(): ArgType = when (this) {
		BOOLEAN -> ArgType.`object`("java.lang.Boolean")
		CHAR -> ArgType.`object`("java.lang.Character")
		BYTE -> ArgType.`object`("java.lang.Byte")
		SHORT -> ArgType.`object`("java.lang.Short")
		INT -> ArgType.`object`("java.lang.Integer")
		FLOAT -> ArgType.`object`("java.lang.Float")
		LONG -> ArgType.`object`("java.lang.Long")
		DOUBLE -> ArgType.`object`("java.lang.Double")
		OBJECT -> ArgType.OBJECT
		ARRAY -> ArgType.OBJECT_ARRAY
		VOID -> ArgType.`object`("java.lang.Void")
	}

	/**
	 * 判断是否为对象或数组类型。
	 * 用于类型推导时区分引用类型和值类型。
	 */
	fun isObjectOrArray(): Boolean = this == OBJECT || this == ARRAY

	override fun toString(): String = longName
}
