package kadx.core.dex.instructions.invokedynamic

import kadx.api.plugins.input.data.IMethodHandle
import kadx.api.plugins.input.data.MethodHandleType
import kadx.api.plugins.input.data.annotations.EncodedType
import kadx.api.plugins.input.data.annotations.EncodedValue
import kadx.api.plugins.input.insns.InsnData
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.attributes.AType
import kadx.core.dex.attributes.nodes.KadxError
import kadx.core.dex.instructions.ConstClassNode
import kadx.core.dex.instructions.ConstStringNode
import kadx.core.dex.instructions.InsnType
import kadx.core.dex.instructions.args.ArgType
import kadx.core.dex.instructions.args.InsnArg
import kadx.core.dex.instructions.args.LiteralArg
import kadx.core.dex.nodes.InsnNode
import kadx.core.utils.EncodedValueUtils
import kadx.core.utils.exceptions.KadxRuntimeException

/**
 * `StringConcatFactory.makeConcatWithConstants` 生成的 invoke-custom 指令解析器。
 *
 * Java 9+ 的字符串拼接会被编译成带“recipe”模板的 invoke-custom。本类把 recipe 里的
 * 占位符还原成 kadx 的 STR_CONCAT 指令：
 * - `\u0001`（argTag）：从指令寄存器取一个动态参数；
 * - `\u0002`（constTag）：从 bootstrap 常量里取一个常量；
 * - 其它字符：作为字面量文本。
 *
 * Kotlin 转换说明：原 Java 静态方法放入 companion + `@JvmStatic`。
 */
class CustomStringConcat {

	companion object {

		/** 判断是否为 `StringConcatFactory.makeConcatWithConstants` 调用。 */
		fun isStringConcat(values: List<EncodedValue>): Boolean {
			if (values.size < 4) {
				return false
			}
			val methodHandle = values[0].value as IMethodHandle
			if (methodHandle.type != MethodHandleType.INVOKE_STATIC) {
				return false
			}
			val methodRef = methodHandle.methodRef ?: return false
			if (methodRef.name != "makeConcatWithConstants") {
				return false
			}
			if (methodRef.parentClassType != "Ljava/lang/invoke/StringConcatFactory;") {
				return false
			}
			if (values[1].value != "makeConcatWithConstants") {
				return false
			}
			return values[3].type == EncodedType.ENCODED_STRING
		}

		/** 解析 recipe 并构建 STR_CONCAT 指令；失败时返回带 KADX_ERROR 的 NOP 指令。 */
		fun buildStringConcat(insn: InsnData, isRange: Boolean, values: List<EncodedValue>): InsnNode {
			try {
				val argsCount = values.size - 3 + insn.regsCount
				val concat = InsnNode(InsnType.STR_CONCAT, argsCount)
				val recipe = values[3].value as String
				processRecipe(recipe, concat, values, insn)
				val resReg = insn.resultReg
				if (resReg != -1) {
					concat.setResult(InsnArg.reg(resReg, ArgType.STRING))
				}
				return concat
			} catch (e: Exception) {
				val nop = InsnNode(InsnType.NOP, 0)
				nop.add(AFlag.SYNTHETIC)
				nop.addAttr(AType.KADX_ERROR, KadxError("Failed to process dynamic string concat: " + e.message, e))
				return nop
			}
		}

		/** 逐码点扫描 recipe，按占位符切分出常量段与动态参数。 */
		private fun processRecipe(recipe: String, concat: InsnNode, values: List<EncodedValue>, insn: InsnData) {
			val len = recipe.length
			var offset = 0
			var argNum = 0
			var constNum = 4
			val sb = StringBuilder(len)
			while (offset < len) {
				val cp = recipe.codePointAt(offset)
				offset += Character.charCount(cp)
				val argTag = cp == 1
				val constTag = cp == 2
				if (argTag || constTag) {
					if (sb.length != 0) {
						concat.addArg(InsnArg.wrapArg(ConstStringNode(sb.toString())))
						sb.setLength(0)
					}
					if (argTag) {
						concat.addArg(InsnArg.reg(insn, argNum++, ArgType.UNKNOWN))
					} else {
						val constArg = buildInsnArgFromEncodedValue(values[constNum++])
						concat.addArg(constArg)
					}
				} else {
					sb.appendCodePoint(cp)
				}
			}
			if (sb.length != 0) {
				concat.addArg(InsnArg.wrapArg(ConstStringNode(sb.toString())))
			}
		}

		/** 把 bootstrap 常量转换成指令参数（literal / class / string 三类）。 */
		private fun buildInsnArgFromEncodedValue(encodedValue: EncodedValue): InsnArg {
			val value = EncodedValueUtils.convertToConstValue(encodedValue)
			if (value == null) {
				return InsnArg.lit(0, ArgType.UNKNOWN)
			}
			if (value is LiteralArg) {
				return value
			}
			if (value is ArgType) {
				return InsnArg.wrapArg(ConstClassNode(value))
			}
			if (value is String) {
				return InsnArg.wrapArg(ConstStringNode(value))
			}
			throw KadxRuntimeException("Can't build insn arg from encoded value: $encodedValue")
		}
	}
}
