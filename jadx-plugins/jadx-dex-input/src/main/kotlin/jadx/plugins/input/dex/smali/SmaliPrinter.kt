package jadx.plugins.input.dex.smali

import jadx.api.plugins.input.data.AccessFlags
import jadx.api.plugins.input.data.AccessFlagsScope.METHOD
import jadx.api.plugins.input.data.ICodeReader
import jadx.plugins.input.dex.sections.DexMethodData
import jadx.plugins.input.dex.sections.DexMethodRef

// TODO: not finished
/**
 * 方法级 smali 反汇编器：输出 .method 签名头 + 逐指令 smali 文本。
 *
 **背景**：[DexMethodData.disassembleMethod]（插件 API）的底层实现；
 * 通过 [ICodeReader.visitInstructions] 拉取指令并经 [SmaliInsnFormat] 分发格式化。
 */
public object SmaliPrinter {

	/**
	 * 把方法反汇编为 smali 文本（无代码体时只输出签名与 .end method）。
	 */
	public fun printMethod(mth: DexMethodData): String {
		val codeWriter = SmaliCodeWriter()
		codeWriter.startLine(".method ")
		codeWriter.add(AccessFlags.format(mth.getAccessFlags(), METHOD))

		val methodRef: DexMethodRef = mth.getMethodRef()
		methodRef.load()
		codeWriter.add(methodRef.getName())
		codeWriter.add('(').addArgs(methodRef.getArgTypes()).add(')')
		codeWriter.add(methodRef.getReturnType())
		codeWriter.incIndent()

		val codeReader: ICodeReader? = mth.getCodeReader()
		if (codeReader != null) {
			codeWriter.startLine(".registers ").add(codeReader.getRegistersCount())
			val insnFormat = SmaliInsnFormat
			val formatterInfo = InsnFormatterInfo(codeWriter, mth)
			codeReader.visitInstructions { insn ->
				codeWriter.startLine()
				formatterInfo.setInsn(insn)
				insnFormat.format(formatterInfo)
			}
			codeWriter.decIndent()
		}
		codeWriter.startLine(".end method")
		return codeWriter.getCode()
	}
}
