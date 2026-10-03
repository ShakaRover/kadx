package jadx.core.codegen

import jadx.core.dex.attributes.AFlag
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.LiteralArg
import jadx.core.dex.instructions.args.PrimitiveType
import jadx.core.dex.nodes.IDexNode
import jadx.core.utils.StringUtils
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import org.slf4j.LoggerFactory

/**
 * 类型与字面量到源码文本的转换工具。
 *
 * **用途**：把 [ArgType] 转成 Java 源码里的类型签名，把常量寄存器值（[LiteralArg]）按类型
 * 渲染成 `1`、`1L`、`0x1`、`(byte) 1`、`'a'` 等字面量。
 *
 * **Kotlin 转换说明**：原 Java 是不可实例化的静态工具类，
 * 这里保留私有构造器 + `companion object`，调用方式 `TypeGen.xxx(...)` 完全不变。
 */
class TypeGen private constructor() {

	companion object {
		private val LOG = LoggerFactory.getLogger(TypeGen::class.java)

		/** 生成 JVM 描述符形式的类型签名（如 `[I`、`Ljava/lang/String;`）。 */
		fun signature(type: ArgType): String {
			val stype = type.getPrimitiveType()
			if (stype == PrimitiveType.OBJECT) {
				return Utils.makeQualifiedObjectName(type.getObject())
			}
			if (stype == PrimitiveType.ARRAY) {
				return '[' + signature(checkNotNull(type.getArrayElement()))
			}
			return checkNotNull(stype).shortName
		}

		fun signatures(types: List<ArgType>): List<String> = Utils.collectionMap(types) { signature(it) }

		/**
		 * 把字面量寄存器参数转成源码字符串（推荐入口）。
		 */
		fun literalToString(arg: LiteralArg, dexNode: IDexNode, fallback: Boolean): String = literalToString(
			arg.literal,
			arg.getType(),
			dexNode.root().getStringUtils(),
			fallback,
			arg.contains(AFlag.EXPLICIT_PRIMITIVE_TYPE),
		)

		/**
		 * 按值类型转换字面量。
		 *
		 * @throws JadxRuntimeException 类型或字面量不合法时抛出
		 */
		fun literalToString(lit: Long, type: ArgType?, dexNode: IDexNode, fallback: Boolean): String = literalToString(lit, type, dexNode.root().getStringUtils(), fallback, false)

		fun literalToString(lit: Long, type: ArgType?, stringUtils: StringUtils, fallback: Boolean, cast: Boolean): String {
			if (type == null || !type.isTypeKnown()) {
				val n = lit.toString()
				if (fallback && Math.abs(lit) > 100) {
					val sb = StringBuilder()
					sb.append(n).append("(0x").append(java.lang.Long.toHexString(lit))
					if (type == null || type.contains(PrimitiveType.FLOAT)) {
						sb.append(", float:").append(java.lang.Float.intBitsToFloat(lit.toInt()))
					}
					if (type == null || type.contains(PrimitiveType.DOUBLE)) {
						sb.append(", double:").append(java.lang.Double.longBitsToDouble(lit))
					}
					sb.append(')')
					return sb.toString()
				}
				return n
			}

			return when (type.getPrimitiveType()) {
				PrimitiveType.BOOLEAN -> if (lit == 0L) "false" else "true"

				PrimitiveType.CHAR -> stringUtils.unescapeChar(lit.toInt().toChar(), cast)

				PrimitiveType.BYTE -> stringUtils.formatByte(lit, cast)

				PrimitiveType.SHORT -> stringUtils.formatShort(lit, cast)

				PrimitiveType.INT -> stringUtils.formatInteger(lit, cast)

				PrimitiveType.LONG -> stringUtils.formatLong(lit, cast)

				PrimitiveType.FLOAT -> StringUtils.formatFloat(java.lang.Float.intBitsToFloat(lit.toInt()))

				PrimitiveType.DOUBLE -> StringUtils.formatDouble(java.lang.Double.longBitsToDouble(lit))

				PrimitiveType.OBJECT, PrimitiveType.ARRAY -> {
					if (lit != 0L) {
						LOG.warn("Wrong object literal: {} for type: {}", lit, type)
						return lit.toString()
					}
					"null"
				}

				else -> throw JadxRuntimeException("Unknown type in literalToString: $type")
			}
		}

		/** 转成“原始”字符串（不加引号、不加后缀），用于部分代码生成场景。 */
		fun literalToRawString(arg: LiteralArg): String? {
			val type = arg.getType()
			val lit = arg.literal
			return when (type.getPrimitiveType()) {
				PrimitiveType.BOOLEAN -> if (lit == 0L) "false" else "true"

				PrimitiveType.CHAR -> lit.toInt().toChar().toString()

				PrimitiveType.BYTE, PrimitiveType.SHORT, PrimitiveType.INT, PrimitiveType.LONG -> lit.toString()

				PrimitiveType.FLOAT -> java.lang.Float.toString(java.lang.Float.intBitsToFloat(lit.toInt()))

				PrimitiveType.DOUBLE -> java.lang.Double.toString(java.lang.Double.longBitsToDouble(lit))

				PrimitiveType.OBJECT, PrimitiveType.ARRAY -> {
					if (lit != 0L) {
						LOG.warn("Wrong object literal: {} for type: {}", lit, type)
						return lit.toString()
					}
					"null"
				}

				else -> null
			}
		}
	}
}
