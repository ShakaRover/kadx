package kadx.gui.utils.ui

import kadx.gui.utils.UiUtils
import kadx.gui.utils.shortcut.Shortcut
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JToggleButton
import javax.swing.KeyStroke

/**
 * 通用 Swing 动作（`Action`）实现。
 *
 * **做什么**：用 [Runnable] 或 `(ActionEvent) -> Unit` 快速构造一个 `AbstractAction`，
 * 并提供名称、描述、图标、选中态、快捷键等便捷设置方法。
 *
 * **为什么方法都声明为 `open`**：原 Java 方法默认可覆写，子类
 * [kadx.gui.ui.action.KadxGuiAction] 覆写了 [actionPerformed] 与 [setKeyBinding]，
 * 必须保持可覆写语义。
 */
open class ActionHandler : AbstractAction {

	private val consumer: (ActionEvent) -> Unit

	constructor(action: Runnable) {
		consumer = { action.run() }
	}

	constructor(consumer: (ActionEvent) -> Unit) {
		this.consumer = consumer
	}

	constructor(name: String, action: Runnable) : this(action) {
		setName(name)
	}

	constructor() {
		consumer = { }
	}

	open fun setName(name: String) {
		putValue(Action.NAME, name)
	}

	/** 设置名称与描述，并返回自身以便链式调用。 */
	open fun withNameAndDesc(name: String): ActionHandler {
		setNameAndDesc(name)
		return this
	}

	/** 同时设置名称与简短描述。 */
	open fun setNameAndDesc(name: String) {
		setName(name)
		setShortDescription(name)
	}

	open fun setShortDescription(desc: String) {
		putValue(Action.SHORT_DESCRIPTION, desc)
	}

	open fun setIcon(icon: Icon) {
		putValue(Action.SMALL_ICON, icon)
	}

	open fun setSelected(selected: Boolean) {
		putValue(Action.SELECTED_KEY, selected)
	}

	open fun setKeyBinding(keyStroke: KeyStroke?) {
		putValue(Action.ACCELERATOR_KEY, keyStroke)
	}

	/** 把快捷键绑定到组件，并同时记录到动作属性中。 */
	open fun attachKeyBindingFor(component: JComponent, keyStroke: KeyStroke) {
		UiUtils.addKeyBinding(component, keyStroke, "run", this)
		setKeyBinding(keyStroke)
	}

	/** 若已设置快捷键，则把快捷键文本追加到描述中。 */
	open fun addKeyBindToDescription() {
		val keyStroke = getValue(Action.ACCELERATOR_KEY) as? KeyStroke
		if (keyStroke != null) {
			val keyText = Shortcut.keyboard(keyStroke.getKeyCode(), keyStroke.getModifiers()).toString()
			val desc = getValue(Action.SHORT_DESCRIPTION) as? String
			setShortDescription(desc + " (" + keyText + ")")
		}
	}

	override fun actionPerformed(e: ActionEvent) {
		consumer(e)
	}

	open fun makeButton(): JButton {
		addKeyBindToDescription()
		return JButton(this)
	}

	open fun makeToggleButton(): JToggleButton {
		val toggleButton = JToggleButton(this)
		toggleButton.setText("")
		return toggleButton
	}

	open fun makeCheckBoxMenuItem(): JCheckBoxMenuItem = JCheckBoxMenuItem(this)
}
