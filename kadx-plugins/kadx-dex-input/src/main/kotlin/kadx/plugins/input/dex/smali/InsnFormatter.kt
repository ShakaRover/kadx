package kadx.plugins.input.dex.smali

/**
 * 单条指令的 smali 格式化器（函数式接口）。
 *
 **背景**：[SmaliInsnFormat] 为每种操作码注册一个实现，从 [InsnFormatterInfo]
 * 取出共享的 [SmaliCodeWriter] 与当前指令，把助记符/寄存器/字面量追加进输出。
 * 声明为 `fun interface` 以便用 lambda 注册（对应原 Java 的方法引用/lambda）。
 */
public fun interface InsnFormatter {

	public fun format(insnFormatInfo: InsnFormatterInfo)
}
