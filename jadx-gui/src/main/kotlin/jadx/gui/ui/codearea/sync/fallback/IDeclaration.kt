package jadx.gui.ui.codearea.sync.fallback

/**
 * 代码中“声明”的抽象：类声明或方法 / 构造器声明。
 *
 * **做什么**：提供用于跨代码区比对的“标识名”（类名或方法名）以及声明所在的行。
 */
interface IDeclaration {
	/** 用于比对的标识名（类名 / 方法名），可能为 null。 */
	fun getIdentifyingName(): String?

	/** 声明所在的行。 */
	fun getLine(): AbstractCodeAreaLine
}
