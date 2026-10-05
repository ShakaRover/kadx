package kadx.plugins.input.java.data.code.decoders

import kadx.plugins.input.java.data.code.CodeDecodeState

/**
 * 指令操作数解码器接口。
 *
 **做什么**：[decode] 在正常解析时读操作数并分配寄存器；
 * [skip] 在指令已被外部解码（如 wide 前缀场景）时只推进读头不重复处理，默认空实现。
 */
// fun interface：原 Java 是单抽象方法函数式接口（lambda 实现），
// Kotlin 侧必须声明为 fun interface 才能保留 SAM 转换能力
fun interface IJavaInsnDecoder {

	fun decode(state: CodeDecodeState)

	// 注意：fun interface 允许带默认实现的附加成员，skip 保持原 Java default 语义
	open fun skip(state: CodeDecodeState) {}
}
