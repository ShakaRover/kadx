package kadx.core.dex.nodes

import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.core.dex.attributes.AType
import kadx.core.dex.info.MethodInfo
import kadx.core.dex.instructions.args.ArgType
import kadx.core.utils.Utils.listToString
import kadx.core.utils.Utils.notEmpty

interface IMethodDetails : IKadxAttribute {
	val methodInfo: MethodInfo

	val returnType: ArgType

	val argTypes: List<ArgType>

	val typeParameters: List<ArgType>

	val throws: List<ArgType>

	val isVarArg: Boolean

	val rawAccessFlags: Int

	override val attrType get() = AType.METHOD_DETAILS

	override fun toAttrString(): String {
		val sb = StringBuilder("MD:")
		if (notEmpty(typeParameters)) {
			sb.append('<').append(listToString(typeParameters)).append(">:")
		}
		sb.append('(').append(listToString(argTypes)).append(')').append(':')
		sb.append(returnType)
		if (isVarArg) {
			sb.append(" VARARG")
		}
		val throwsList = throws
		if (notEmpty(throwsList)) {
			sb.append(" throws ").append(listToString(throwsList))
		}
		return sb.toString()
	}
}
