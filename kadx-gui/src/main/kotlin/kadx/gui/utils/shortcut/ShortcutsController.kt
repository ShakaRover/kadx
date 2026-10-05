package kadx.gui.utils.shortcut

import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.gui.settings.KadxSettings
import kadx.gui.settings.data.ShortcutsWrapper
import kadx.gui.ui.MainWindow
import kadx.gui.ui.action.ActionCategory
import kadx.gui.ui.action.ActionModel
import kadx.gui.ui.action.IShortcutAction
import kadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.MouseEvent
import java.util.EnumMap
import javax.swing.JComponent
import javax.swing.KeyStroke

/**
 * 快捷键控制器。
 *
 * **做什么**：管理 [ActionModel] 到 [Shortcut] 的绑定、把快捷键应用到各个
 * [IShortcutAction]、监听全局鼠标事件以触发鼠标快捷键。
 *
 * **线程模型**：完全沿用 Swing 单线程模型，动作通过 [UiUtils.uiRun] 派发到 EDT。
 */
class ShortcutsController(private val settings: KadxSettings) {

	private val boundActions: MutableMap<ActionModel, MutableSet<IShortcutAction>> = EnumMap(ActionModel::class.java)
	private val mouseActions: MutableMap<Int, MutableList<IShortcutAction>> = HashMap()

	private lateinit var shortcuts: ShortcutsWrapper

	/** 从设置加载快捷键并刷新已绑定动作。 */
	fun loadSettings() {
		shortcuts = settings.shortcuts
		indexMouseActions()
		boundActions.forEach { (actionModel, actions) ->
			val shortcut = get(actionModel)
			actions.forEach { action -> action.setShortcut(shortcut) }
		}
	}

	/** 获取某动作当前的快捷键。 */
	fun get(actionModel: ActionModel): Shortcut? = shortcuts.get(actionModel)

	/** 获取某动作对应的 [KeyStroke]；非键盘快捷键返回 `null`。 */
	fun getKeyStroke(actionModel: ActionModel): KeyStroke? {
		val shortcut = get(actionModel)
		if (shortcut != null && shortcut.isKeyboard) {
			return shortcut.toKeyStroke()
		}
		return null
	}

	/**
	 * 绑定动作，并在每次 [loadSettings] 时更新其快捷键。
	 */
	fun bind(action: IShortcutAction) {
		if (action.getShortcutComponent() == null) {
			LOG.warn("No shortcut component in action: {}", action, KadxRuntimeException())
			return
		}
		boundActions.getOrPut(checkNotNull(action.getActionModel())) { HashSet() }.add(action)
	}

	/**
	 * 立即为动作设置快捷键。
	 */
	fun bindImmediate(action: IShortcutAction) {
		bind(action)
		val shortcut = get(checkNotNull(action.getActionModel()))
		action.setShortcut(shortcut)
	}

	/** 注册全局鼠标事件监听，用于触发鼠标快捷键。 */
	fun registerMouseEventListener(mw: MainWindow) {
		Toolkit.getDefaultToolkit().addAWTEventListener(
			{ event ->
				if (mw.isSettingsOpen) {
					return@addAWTEventListener
				}
				if (event !is MouseEvent) {
					return@addAWTEventListener
				}
				if (event.getID() != MouseEvent.MOUSE_PRESSED) {
					return@addAWTEventListener
				}
				val actions = mouseActions[event.getButton()]
				if (actions != null) {
					for (action in actions) {
						event.consume()
						UiUtils.uiRun(Runnable { action.performAction() })
					}
				}
			},
			AWTEvent.MOUSE_EVENT_MASK,
		)
	}

	private fun indexMouseActions() {
		mouseActions.clear()
		for (actionModel in ActionModel.values()) {
			val shortcut = shortcuts.get(actionModel)
			if (shortcut.isMouse) {
				val actions = boundActions[actionModel]
				if (actions != null && actions.isNotEmpty()) {
					mouseActions.getOrPut(checkNotNull(shortcut.getMouseButton())) { ArrayList() }.addAll(actions)
				}
			}
		}
	}

	/** 解除某组件上绑定的所有动作。 */
	fun unbindActionsForComponent(component: JComponent) {
		for (actions in boundActions.values) {
			actions.removeAll { action ->
				action.getShortcutComponent() == null || action.getShortcutComponent() === component
			}
		}
	}

	/**
	 * 只保留绑定到主窗口的动作，其余动作按需再添加。
	 */
	fun reset() {
		for (actionModel in ActionModel.values()) {
			if (actionModel.category != ActionCategory.MENU_TOOLBAR) {
				boundActions.remove(actionModel)
			}
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ShortcutsController::class.java)

		/** 返回所有动作的默认快捷键映射。 */
		val default: Map<ActionModel, Shortcut> get() {
			val shortcuts = HashMap<ActionModel, Shortcut>()
			for (actionModel in ActionModel.values()) {
				shortcuts[actionModel] = actionModel.defaultShortcut
			}
			return shortcuts
		}
	}
}
