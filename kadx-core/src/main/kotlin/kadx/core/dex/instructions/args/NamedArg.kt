package kadx.core.dex.instructions.args

class NamedArg(argName: String, type: ArgType) :
	InsnArg(),
	Named {
	override var name: String? = argName

	init {
		this.type = type
	}

	override val isNamed: Boolean get() = true

	override fun duplicate(): InsnArg = copyCommonParams(NamedArg(checkNotNull(name), type))

	override fun hashCode(): Int = name.hashCode()

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o !is NamedArg) {
			return false
		}
		return name == o.name
	}
}
