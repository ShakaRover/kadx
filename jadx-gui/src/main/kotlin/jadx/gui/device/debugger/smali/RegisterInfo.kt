package jadx.gui.device.debugger.smali

import jadx.api.plugins.input.data.ILocalVar

/**
 * 调试信息中「寄存器 / 局部变量」的抽象基类。
 *
 * **做什么**：实现 [ILocalVar] 中与「变量作用域」相关的通用逻辑：
 * 一个变量在 `[startOffset, endOffset)` 区间内才算「已初始化 / 可见」。
 * 具体子类（如 [SmaliRegister]）负责提供偏移量与名字。
 *
 * **为什么保留为 `abstract class`**：原 Java 是抽象类，GUI 的 `SmaliRegister`
 * 与 `SmaliDebugger.RuntimeVarInfo` 都继承它；继承层级必须保持不变。
 */
abstract class RegisterInfo : ILocalVar {

	/**
	 * 判断在 [codeOffset] 处该变量是否已经生效。
	 *
	 * 注意：这里用的是闭开区间 `[start, end)`，即起始偏移算「已初始化」。
	 */
	open fun isInitialized(codeOffset: Long): Boolean = codeOffset >= startOffset && codeOffset < endOffset

	/**
	 * 判断在 [codeOffset] 处该变量是否尚未生效（作用域之外）。
	 *
	 * 与 [isInitialized] 互补：偏移量小于起始或大于等于结束都算未初始化。
	 */
	open fun isUnInitialized(codeOffset: Long): Boolean = codeOffset < startOffset || codeOffset >= endOffset
}
