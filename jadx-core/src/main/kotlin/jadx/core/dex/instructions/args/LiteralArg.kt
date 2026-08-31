package jadx.core.dex.instructions.args

import jadx.core.codegen.TypeGen
import jadx.core.utils.StringUtils
import jadx.core.utils.exceptions.JadxRuntimeException

class LiteralArg private constructor(value: Long, type: ArgType) : InsnArg() {
	val literal: Long

	init {
		if (value != 0L && type.isObject()) {
			throw JadxRuntimeException("Wrong literal type: $type for value: $value")
		}
		literal = value
		this.type = type
	}

	override val isLiteral: Boolean get() = true

	override fun isZeroLiteral(): Boolean = literal == 0L

	fun isInteger(): Boolean = when (type.getPrimitiveType()) {
		PrimitiveType.INT, PrimitiveType.BYTE, PrimitiveType.CHAR, PrimitiveType.SHORT, PrimitiveType.LONG -> true
		else -> false
	}

	fun isNegative(): Boolean {
		if (isInteger()) {
			return literal < 0L
		}
		if (type == ArgType.FLOAT) {
			val fVal = java.lang.Float.intBitsToFloat(literal.toInt())
			return fVal < 0f && fVal.isFinite()
		}
		if (type == ArgType.DOUBLE) {
			val dVal = java.lang.Double.longBitsToDouble(literal)
			return dVal < 0.0 && dVal.isFinite()
		}
		return false
	}

	fun negate(): LiteralArg? {
		var neg: Long
		if (isInteger()) {
			neg = -literal
		} else if (type == ArgType.FLOAT) {
			val fVal = java.lang.Float.intBitsToFloat(literal.toInt())
			neg = java.lang.Float.floatToIntBits(-fVal).toLong()
		} else if (type == ArgType.DOUBLE) {
			val dVal = java.lang.Double.longBitsToDouble(literal)
			neg = java.lang.Double.doubleToLongBits(-dVal)
		} else {
			return null
		}
		return LiteralArg(neg, type)
	}

	override fun duplicate(): InsnArg = copyCommonParams(LiteralArg(literal, type))

	override fun hashCode(): Int = (literal xor (literal ushr 32)).toInt() + 31 * getType().hashCode()

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o == null || javaClass != o.javaClass) {
			return false
		}
		val that = o as LiteralArg
		return literal == that.literal && getType() == that.getType()
	}

	override fun toShortString(): String = literal.toString()

	override fun toString(): String {
		try {
			val value = TypeGen.literalToString(literal, getType(), StringUtils.getInstance(), true, false)
			if (getType() == ArgType.BOOLEAN && (value == "true" || value == "false")) {
				return value
			}
			return "($value $type)"
		} catch (ex: JadxRuntimeException) {
			// can't convert literal to string
			return "($literal $type)"
		}
	}

	companion object {
		@JvmStatic
		fun make(value: Long, type: ArgType): LiteralArg = LiteralArg(value, type)

		@JvmStatic
		fun makeWithFixedType(value: Long, type: ArgType): LiteralArg = LiteralArg(value, fixLiteralType(value, type))

		private fun fixLiteralType(value: Long, type: ArgType): ArgType {
			if (value == 0L || type.isTypeKnown() || type.contains(PrimitiveType.LONG) || type.contains(PrimitiveType.DOUBLE)) {
				return type
			}
			if (value == 1L) {
				return ArgType.NARROW_NUMBERS
			}
			if (value < 0) {
				return ArgType.NARROW_NEG_NUMBERS
			}
			return ArgType.NARROW_NUMBERS_NO_BOOL
		}

		@JvmStatic
		fun litFalse(): LiteralArg = make(0L, ArgType.BOOLEAN)

		@JvmStatic
		fun litTrue(): LiteralArg = make(1L, ArgType.BOOLEAN)
	}
}
