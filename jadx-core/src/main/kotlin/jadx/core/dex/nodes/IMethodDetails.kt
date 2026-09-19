package jadx.core.dex.nodes

import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.dex.attributes.AType
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.args.ArgType
import jadx.core.utils.Utils.listToString
import jadx.core.utils.Utils.notEmpty

interface IMethodDetails : IJadxAttribute {
	fun getMethodInfo(): MethodInfo

	fun getReturnType(): ArgType

	fun getArgTypes(): List<ArgType>

	fun getTypeParameters(): List<ArgType>

	fun getThrows(): List<ArgType>

	fun isVarArg(): Boolean

	fun getRawAccessFlags(): Int

	override fun getAttrType() = AType.METHOD_DETAILS

	override fun toAttrString(): String {
		val sb = StringBuilder("MD:")
		if (notEmpty(getTypeParameters())) {
			sb.append('<').append(listToString(getTypeParameters())).append(">:")
		}
		sb.append('(').append(listToString(getArgTypes())).append(')').append(':')
		sb.append(getReturnType())
		if (isVarArg()) {
			sb.append(" VARARG")
		}
		val throwsList = getThrows()
		if (notEmpty(throwsList)) {
			sb.append(" throws ").append(listToString(throwsList))
		}
		return sb.toString()
	}
}
