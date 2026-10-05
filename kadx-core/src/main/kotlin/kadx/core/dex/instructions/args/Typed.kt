package kadx.core.dex.instructions.args

import kadx.core.dex.attributes.AttrNode

abstract class Typed : AttrNode() {
	@JvmField protected var type: ArgType = ArgType.UNKNOWN

	open fun getType(): ArgType = type

	open fun setType(type: ArgType) {
		this.type = type
	}

	open fun isTypeImmutable(): Boolean = false
}
