package jadx.gui.ui.action

import jadx.gui.ui.codearea.CodeArea

/**
 * 绑定到代码区（[CodeArea]）的动作基类。
 *
 * **做什么**：保存目标代码区，并把快捷键绑定到该代码区组件上；
 * [dispose] 时释放引用，避免动作对象长期持有已关闭的代码区。
 */
open class CodeAreaAction : JadxGuiAction {
	/**
	 * 目标代码区。
	 *
	 * 使用 `@JvmField` 暴露给 Java 子类直接读写（原 Java 为 `protected` 字段）；
	 * `@Transient` 保持原 `transient` 语义，避免随 Swing Action 序列化。
	 */
	@Transient
	@JvmField
	protected var codeArea: CodeArea? = null

	constructor(actionModel: ActionModel, codeArea: CodeArea) : super(actionModel) {
		this.codeArea = codeArea
		setShortcutComponent(codeArea)
	}

	constructor(id: String, codeArea: CodeArea) : super(id) {
		this.codeArea = codeArea
		setShortcutComponent(codeArea)
	}

	/** 释放代码区引用。 */
	open fun dispose() {
		codeArea = null
	}

	/** 返回目标代码区；已释放时抛异常。 */
	fun getCodeArea(): CodeArea = checkNotNull(codeArea)
}
