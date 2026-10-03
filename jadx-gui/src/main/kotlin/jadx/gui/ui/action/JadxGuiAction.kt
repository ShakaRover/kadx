package jadx.gui.ui.action

import jadx.gui.ui.menu.JadxMenu
import jadx.gui.utils.UiUtils
import jadx.gui.utils.shortcut.Shortcut
import jadx.gui.utils.ui.ActionHandler
import java.awt.event.ActionEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * jadx GUI 所有动作的基类。
 *
 * **做什么**：在 Swing 的 [ActionHandler]（继承自 `AbstractAction`）之上补充
 * “动作元数据（[ActionModel]）”“快捷键绑定”与“统一触发”逻辑，并实现
 * [IShortcutAction]，供 [jadx.gui.utils.shortcut.ShortcutsController] 统一管理。
 *
 * **线程模型**：完全沿用 Swing 的单线程模型，`actionPerformed` 仍在 EDT 上执行，
 * 不引入任何协程或后台线程。
 *
 * **为什么保留显式 `getActionModel()` 等函数**：该类的调用方大量为 Java，
 * 显式函数可保证 JVM 表面零改动。
 */
open class JadxGuiAction :
	ActionHandler,
	IShortcutAction {
	/** 动作元数据；用字符串 id 构造时为 null。 */
	private val actionModel: ActionModel?

	/** 动作标识（用于 Swing 键绑定注册/注销）。 */
	private val id: String

	/** 绑定快捷键的目标组件。 */
	private var shortcutComponent: JComponent? = null

	/** 上一次注册到组件上的按键，注销时需要。 */
	private var addedKeyStroke: KeyStroke? = null

	/** 当前快捷键。 */
	private var shortcut: Shortcut? = null

	constructor(actionModel: ActionModel) : super() {
		this.actionModel = actionModel
		this.id = actionModel.name
		updateProperties()
	}

	constructor(actionModel: ActionModel, action: Runnable) : super(action) {
		this.actionModel = actionModel
		this.id = actionModel.name
		updateProperties()
	}

	constructor(actionModel: ActionModel, consumer: (ActionEvent) -> Unit) : super(consumer) {
		this.actionModel = actionModel
		this.id = actionModel.name
		updateProperties()
	}

	constructor(id: String) : super() {
		this.actionModel = null
		this.id = id
		updateProperties()
	}

	/** 从 [ActionModel] 同步名称、描述与图标到 Swing Action 属性。 */
	private fun updateProperties() {
		val model = actionModel ?: return
		setName(model.getName())
		setShortDescription(model.getDescription())
		val icon = model.getIcon()
		if (icon != null) {
			setIcon(icon)
		}
	}

	override fun getActionModel(): ActionModel? = actionModel

	override fun setShortcut(shortcut: Shortcut?) {
		this.shortcut = shortcut
		if (shortcut != null) {
			setKeyBinding(shortcut.toKeyStroke())
		} else {
			setKeyBinding(null)
		}
	}

	/** 设置快捷键绑定的目标组件。 */
	fun setShortcutComponent(component: JComponent?) {
		this.shortcutComponent = component
	}

	override fun getShortcutComponent(): JComponent? = shortcutComponent

	override fun actionPerformed(e: ActionEvent) {
		super.actionPerformed(e)
	}

	override fun performAction() {
		val component = shortcutComponent
		if (component != null) {
			if (component === JadxMenu.JADX_MENU_COMPONENT) {
				// 菜单占位组件始终视为可用
			} else if (!component.isShowing) {
				return
			}
		}
		val shortcutType = shortcut?.getTypeString() ?: "null"
		actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, COMMAND_PREFIX + shortcutType))
	}

	override fun setKeyBinding(keyStroke: KeyStroke?) {
		val component = shortcutComponent
		if (component == null) {
			super.setKeyBinding(keyStroke)
		} else {
			// 仅为让菜单项右侧显示快捷键（置灰展示）
			super.setKeyBinding(keyStroke)

			val added = addedKeyStroke
			if (added != null) {
				UiUtils.removeKeyBinding(component, added, id)
			}
			val key = keyStroke
			if (key != null) {
				UiUtils.addKeyBinding(component, key, id) { performAction() }
			}
			addedKeyStroke = keyStroke
		}
	}

	override fun toString(): String = "JadxGuiAction{" + id + ", component: " + shortcutComponent + '}'

	companion object {
		private const val COMMAND_PREFIX = "JadxGuiAction.Command."

		/** 判断该事件是否由本类触发（通过 action command 前缀识别）。 */
		@JvmStatic
		fun isSource(event: ActionEvent): Boolean {
			val command = event.actionCommand
			return command != null && command.startsWith(COMMAND_PREFIX)
		}
	}
}
