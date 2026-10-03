package jadx.plugins.input.java.data.attributes.debuginfo

import jadx.api.plugins.input.data.ILocalVar
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
	regNum: Int,
	nameValue: String?,
	typeValue: String?,
	sign: String?,
	private val startOffset: Int,
	private val endOffset: Int,
) : ILocalVar {

	// 寄存器号会被 shiftRegNum 修改，故为 var；其余字段解析后不变
	private var regNum: Int = regNum
	private val name: String? = nameValue
	private val type: String? = typeValue
	private var sign: String? = sign

	fun shiftRegNum(maxStack: Int) {
		regNum += maxStack // convert local var to register
	}

	// 接口声明非空；损坏 class 时名字为 null，调用方解引用与原 Java 一样 NPE
	override fun getName(): String = name ?: throw NullPointerException("name is null")

	override fun getRegNum(): Int = regNum

	// 接口声明非空，但 LocalVariableTypeTable 来源的变量此处运行时为 null（原 Java 同样返回 null）；
	// 调用方解引用时两边都是 NPE，行为等价
	override fun getType(): String = type ?: throw NullPointerException("type is null")

	@Nullable
	override fun getSignature(): String? = sign

	fun setSignature(sign: String?) {
		this.sign = sign
	}

	override fun getStartOffset(): Int = startOffset

	override fun getEndOffset(): Int = endOffset

	override fun isMarkedAsParameter(): Boolean = false

	override fun hashCode(): Int {
		var result = regNum
		result = 31 * result + name.hashCode()
		result = 31 * result + startOffset
		result = 31 * result + endOffset
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
		return regNum == other.regNum &&
			startOffset == other.startOffset &&
			endOffset == other.endOffset &&
			name == other.name
	}

	companion object {
		private fun formatOffset(offset: Int): String = String.format("0x%04x", offset)
	}

	override fun toString(): String = formatOffset(startOffset) + '-' + formatOffset(endOffset) +
		": r" + regNum + " '" + name + "' " + type +
		(if (sign != null) ", signature: " + sign else "")
}
