package jadx.core.dex.nodes.parser

import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.core.dex.attributes.IAttributeNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 泛型签名解析器（JVMS §4.7.9.1 Signature 属性）。
 *
 * 它把形如 `<T:Ljava/lang/Object;>Ljava/util/List<TT;>;` 的签名字符串解析成
 * jadx 的 [ArgType]。内部维护一个游标 [pos] 与标记 [mark]，通过
 * `next()`/`lookAhead()`/`mark()`/`slice()` 等辅助方法逐字符扫描。
 *
 * Kotlin 转换说明：静态工厂 [fromNode]/[getSignature] 放入 companion + `@JvmStatic`；
 * 原 Java 的 `private static final char STOP_CHAR = 0` 用 companion 常量平替。
 */
class SignatureParser(private val sign: String) {

	private val end: Int = sign.length
	private var pos: Int = -1
	private var mark: Int = 0

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(SignatureParser::class.java)

		/** 游标越界时返回的哨兵字符（原 Java 用 0）。 */
		private const val STOP_CHAR = '\u0000'

		/** 从属性节点读取签名并创建解析器；无签名时返回 null。 */
		fun fromNode(node: IAttributeNode): SignatureParser? {
			val signature = getSignature(node) ?: return null
			return SignatureParser(signature)
		}

		/** 读取节点上的签名属性；不存在时返回 null。 */
		fun getSignature(node: IAttributeNode): String? {
			val attr = node.get(JadxAttrType.SIGNATURE) ?: return null
			return attr.signature
		}
	}

	/** 读取下一个字符；越界时返回 [STOP_CHAR]。 */
	private fun next(): Char {
		pos++
		if (pos >= end) {
			return STOP_CHAR
		}
		return sign[pos]
	}

	/** 预读下一个字符是否为 [ch]（不移动游标）。 */
	private fun lookAhead(ch: Char): Boolean {
		val next = pos + 1
		return next < end && sign[next] == ch
	}

	private fun mark() {
		mark = pos
	}

	/**
	 * 左闭右开切片。
	 *
	 * @return 从 [mark] 到当前游标（不含当前字符）的子串
	 */
	private fun slice(): String {
		val start = if (mark == -1) 0 else mark
		if (start >= pos) {
			return ""
		}
		return sign.substring(start, pos)
	}

	/** 左闭右闭切片（包含当前字符）。 */
	private fun inclusiveSlice(): String {
		var start = mark
		if (start == -1) {
			start = 0
		}
		val last = pos + 1
		if (start >= last) {
			return ""
		}
		return sign.substring(start, last)
	}

	/** 向前扫描直到遇到 [untilChar]；到结尾仍未遇到则回退游标并返回 false。 */
	private fun skipUntil(untilChar: Char): Boolean {
		val startPos = pos
		while (true) {
			if (lookAhead(untilChar)) {
				return true
			}
			val ch = next()
			if (ch == STOP_CHAR) {
				pos = startPos
				return false
			}
		}
	}

	/** 消费期望字符，不符则抛异常。 */
	private fun consume(exp: Char) {
		val c = next()
		if (exp != c) {
			throw JadxRuntimeException("Consume wrong char: '$c' != '$exp', sign: ${debugString()}")
		}
	}

	/** 若下一个字符是 [exp] 则消费并返回 true。 */
	private fun tryConsume(exp: Char): Boolean {
		if (lookAhead(exp)) {
			next()
			return true
		}
		return false
	}

	/** 消费直到 [lastChar]（含），返回子串；未找到时返回 null。 */
	fun consumeUntil(lastChar: Char): String? {
		mark()
		return if (skipUntil(lastChar)) inclusiveSlice() else null
	}

	/**
	 * 解析一个类型。
	 *
	 * 支持：对象类型 `L...;`、类型变量 `T...;`、数组 `[...`、基本类型单字符；
	 * 遇到结束符返回 null。
	 */
	fun consumeType(): ArgType? {
		val ch = next()
		when (ch) {
			'L' -> {
				val obj = consumeObjectType(false)
				if (obj != null) {
					return obj
				}
			}

			'T' -> {
				next()
				mark()
				val typeVarName = consumeUntil(';')
				if (typeVarName != null) {
					consume(';')
					if (typeVarName.contains(")")) {
						throw JadxRuntimeException("Bad name for type variable: $typeVarName")
					}
					return ArgType.genericType(typeVarName)
				}
			}

			'[' -> return ArgType.array(checkNotNull(consumeType()))

			STOP_CHAR -> return null

			else -> {
				// 基本类型（单字符）
				return ArgType.parse(ch)
			}
		}
		throw JadxRuntimeException("Can't parse type: ${debugString()}, unexpected: $ch")
	}

	/** 解析一个类型列表，直到遇到结束符。 */
	fun consumeTypeList(): List<ArgType> {
		val list = ArrayList<ArgType>()
		while (true) {
			val type = consumeType() ?: break
			list.add(type)
		}
		return if (list.isEmpty()) emptyList() else list
	}

	/**
	 * 解析对象类型（`L...;` 或带泛型的 `L...<...>;`）。
	 *
	 * @param innerType 当前是否在解析内部类（内部类用 '.' 分隔，停止于下一个嵌套内部类之前）
	 */
	private fun consumeObjectType(innerType: Boolean): ArgType? {
		mark()
		var ch: Char
		do {
			if (innerType && lookAhead('.')) {
				// 在下一个嵌套内部类之前停止
				return ArgType.`object`(inclusiveSlice())
			}
			ch = next()
			if (ch == STOP_CHAR) {
				return null
			}
		} while (ch != '<' && ch != ';')

		if (ch == ';') {
			val obj = if (innerType) slice().replace('/', '.') else inclusiveSlice()
			return ArgType.`object`(obj)
		}
		// 泛型开始（'<'）
		var obj = slice()
		if (!innerType) {
			obj += ';'
		} else {
			obj = obj.replace('/', '.')
		}
		val typeVars = consumeGenericArgs()
		consume('>')

		var genericType = ArgType.generic(obj, typeVars)
		if (!lookAhead('.')) {
			consume(';')
			return genericType
		}
		consume('.')
		next()
		// 类型解析尚未结束，继续解析内部类
		var inner = consumeObjectType(true)
			?: throw JadxRuntimeException("No inner type found: ${debugString()}")
		// 每个嵌套内部类都创建一个嵌套类型对象
		while (lookAhead('.')) {
			genericType = ArgType.outerGeneric(genericType, inner)
			consume('.')
			next()
			inner = consumeObjectType(true)
				?: throw JadxRuntimeException("Unexpected inner type found: ${debugString()}")
		}
		return ArgType.outerGeneric(genericType, inner)
	}

	/** 解析泛型实参列表，支持通配符 `*`、`+`（extends）、`-`（super）。 */
	private fun consumeGenericArgs(): List<ArgType> {
		val list = ArrayList<ArgType>()
		var type: ArgType?
		do {
			if (lookAhead('*')) {
				next()
				type = ArgType.wildcard()
			} else if (lookAhead('+')) {
				next()
				type = ArgType.wildcard(checkNotNull(consumeType()), ArgType.WildcardBound.EXTENDS)
			} else if (lookAhead('-')) {
				next()
				type = ArgType.wildcard(checkNotNull(consumeType()), ArgType.WildcardBound.SUPER)
			} else {
				type = consumeType()
			}
			if (type != null) {
				list.add(type)
			}
		} while (type != null && !lookAhead('>'))
		return list
	}

	/**
	 * 解析泛型类型参数映射，例如 `&lt;T:Ljava/lang/Exception;:Ljava/lang/Object;&gt;`。
	 */
	fun consumeGenericTypeParameters(): List<ArgType> {
		if (!lookAhead('<')) {
			return emptyList()
		}
		val list = ArrayList<ArgType>()
		consume('<')
		while (true) {
			if (lookAhead('>') || next() == STOP_CHAR) {
				break
			}
			val id = consumeUntil(':') ?: throw JadxRuntimeException("Failed to parse generic types map")
			consume(':')
			tryConsume(':')
			val types = consumeExtendsTypesList()
			list.add(ArgType.genericType(id, types))
		}
		consume('>')
		return list
	}

	/**
	 * 解析以 ':' 分隔的类型列表，最后一个类型是 `java.lang.Object`（会被忽略）。
	 *
	 * 例如：`Ljava/lang/Exception;:Ljava/lang/Object;`
	 */
	private fun consumeExtendsTypesList(): List<ArgType> {
		val types = ArrayList<ArgType>()
		var next: Boolean
		do {
			val argType = consumeType() ?: throw JadxRuntimeException("Unexpected end of signature")
			if (argType != ArgType.OBJECT) {
				types.add(argType)
			}
			next = lookAhead(':')
			if (next) {
				consume(':')
			}
		} while (next)
		return if (types.isEmpty()) emptyList() else types
	}

	/** 解析方法参数列表（`(...)`）；[argsCount] 仅用于预分配与防死循环。 */
	fun consumeMethodArgs(argsCount: Int): List<ArgType> {
		consume('(')
		if (lookAhead(')')) {
			consume(')')
			return emptyList()
		}
		val args = ArrayList<ArgType>(argsCount)
		val limit = argsCount + 10 // 仅防止死循环，合成方法的参数个数可能不同
		do {
			val type = consumeType() ?: throw JadxRuntimeException("Unexpected end of signature")
			args.add(type)
			if (args.size > limit) {
				throw JadxRuntimeException("Arguments count limit reached: ${args.size}")
			}
		} while (!lookAhead(')'))
		consume(')')
		return args
	}

	/** 拼接多个签名字符串（当前未使用，保留与原 Java 一致的工具方法）。 */
	private fun mergeSignature(list: List<String>): String {
		if (list.size == 1) {
			return list[0]
		}
		val sb = StringBuilder()
		for (s in list) {
			sb.append(s)
		}
		return sb.toString()
	}

	/** 返回完整签名字符串。 */
	fun getSignature(): String = sign

	/** 生成带位置信息的调试字符串。 */
	private fun debugString(): String {
		if (pos >= sign.length) {
			return sign
		}
		return "$sign at position $pos ('${sign[pos]}')"
	}

	override fun toString(): String {
		if (pos == -1) {
			return sign
		}
		return sign.substring(0, mark) + '{' + sign.substring(mark, pos) + '}' + sign.substring(pos)
	}
}
