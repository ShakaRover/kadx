package kadx.plugins.input.java.data.attributes.debuginfo

import kadx.api.plugins.input.data.ILocalVar
import org.jetbrains.annotations.Nullable

/**
 * class 文件调试信息中的单个局部变量（含方法参数）。
 *
 **做什么**：保存寄存器号、名字、描述符/泛型签名与作用域偏移；
 * [shiftRegNum] 把"局部变量编号"换算成 JVM 寄存器号（实例方法要加上 this + maxStack 占位）。
 *
 **为什么 type/sign 可空**：LocalVariableTable 条目只有描述符没有签名（sign=null），
 * LocalVariableTypeTable 条目相反（type=null）；两者在 [JavaCodeReader] 里按变量合并互补。
 */
class JavaLocalVar(
	regNumValue: Int,
	nameValue: String?,
	typeValue: String?,
	sign: String?,
	private val startOffsetValue: Int,
	private val endOffsetValue: Int,
) : ILocalVar {

	// 寄存器号会被 shiftRegNum 修改，故为 var；其余字段解析后不变
	private var regNumValue: Int = regNumValue
	private val nameValue: String? = nameValue
	private val typeValue: String? = typeValue
	private var sign: String? = sign

	fun shiftRegNum(maxStack: Int) {
		regNumValue += maxStack // convert local var to register
	}

	// 接口声明非空；损坏 class 时名字为 null，调用方解引用与原 Java 一样 NPE
	override val name: String get() = nameValue ?: throw NullPointerException("name is null")

	override val regNum: Int get() = regNumValue

	// 接口声明非空，但 LocalVariableTypeTable 来源的变量此处运行时为 null（原 Java 同样返回 null）；
	// 调用方解引用时两边都是 NPE，行为等价
	override val type: String get() = typeValue ?: throw NullPointerException("type is null")

	@get:Nullable
	override val signature: String? get() = sign

	fun setSignature(sign: String?) {
		this.sign = sign
	}

	override val startOffset: Int get() = startOffsetValue

	override val endOffset: Int get() = endOffsetValue

	override val isMarkedAsParameter: Boolean get() = false

	override fun hashCode(): Int {
		var result = regNumValue
		result = 31 * result + nameValue.hashCode()
		result = 31 * result + startOffsetValue
		result = 31 * result + endOffsetValue
		return result
	}

	override fun equals(o: Any?): Boolean {
		if (this === o) {
			return true
		}
		if (o !is JavaLocalVar) {
			return false
		}
		val other = o
		return regNumValue == other.regNum &&
			startOffsetValue == other.startOffset &&
			endOffsetValue == other.endOffset &&
			nameValue == other.name
	}

	companion object {
		private fun formatOffset(offset: Int): String = String.format("0x%04x", offset)
	}

	override fun toString(): String = formatOffset(startOffsetValue) + '-' + formatOffset(endOffsetValue) +
		": r" + regNumValue + " '" + nameValue + "' " + typeValue +
		(if (sign != null) ", signature: " + sign else "")
}
