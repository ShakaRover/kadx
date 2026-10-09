package kadx.api.plugins.gui

import kadx.api.gui.tree.ITreeNode
import kadx.api.metadata.ICodeNodeRef
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import javax.swing.ImageIcon
import javax.swing.JFrame

/**
 * kadx-gui 插件上下文：插件通过它访问 GUI 能力。
 *
 * **做什么**：在 UI 线程执行代码、注册菜单/弹窗项、绑定快捷键、访问剪贴板与 GUI 设置、
 * 获取主窗口/图标、以及查询当前光标下的节点等。
 *
 * **为什么保持 Java 可实现**：唯一实现类是 kadx-gui 的 `GuiPluginContext`（Java）。
 * `getNodeUnder*` 系列在原 Java 中未标注但实现会返回 null，这里如实标为可空。
 */
interface KadxGuiContext {

	/**
	 * 在 UI 线程中运行代码。
	 */
	fun uiRun(runnable: Runnable)

	/**
	 * 添加代码查看器右键弹窗菜单项。
	 *
	 * @param name       菜单标题
	 * @param enabled    是否可用判定，弹窗创建时调用（可为 null）
	 * @param keyBinding 可选快捷键字符串（见 `KeyStroke.getKeyStroke(String)`）
	 */
	fun addPopupMenuAction(
		name: String,
		enabled: Function<ICodeNodeRef, Boolean>?,
		keyBinding: String?,
		action: Consumer<ICodeNodeRef>,
	)

	/**
	 * 为树节点添加右键弹窗菜单项。
	 *
	 * @param name         菜单标题
	 * @param addPredicate 是否为该节点添加，弹窗创建时调用
	 */
	@ApiStatus.Experimental
	fun addTreePopupMenuEntry(name: String, addPredicate: Predicate<ITreeNode>, action: Consumer<ITreeNode>)

	/**
	 * 为主窗口绑定全局快捷键。
	 *
	 * @param id         唯一 ID 字符串
	 * @param keyBinding 快捷键字符串（见 `KeyStroke.getKeyStroke(String)`）
	 * @param action     触发的动作
	 * @return 已注册时返回 false
	 */
	fun registerGlobalKeyBinding(id: String, keyBinding: String, action: Runnable): Boolean

	fun copyToClipboard(str: String)

	/**
	 * 访问 GUI 设置。
	 */
	fun settings(): KadxGuiSettings

	/**
	 * 主窗口组件，可作为新建窗口/对话框的父组件。
	 */
	fun getMainFrame(): JFrame

	/**
	 * 从 kadx 资源加载 SVG 图标。
	 * 全部可用图标见 `kadx-gui/src/main/resources/icons`；本方法线程安全。
	 *
	 * @param name 短名称，形如 `category/iconName`，例如 `nodes/publicClass`
	 * @return 已加载并缓存的图标；找不到时返回默认图标 `ui/error`
	 */
	fun getSVGIcon(name: String): ImageIcon

	/** 光标下的节点；无则为 null。 */
	fun getNodeUnderCaret(): ICodeNodeRef?

	/** 鼠标下的节点；无则为 null。 */
	fun getNodeUnderMouse(): ICodeNodeRef?

	/** 光标下最外层节点；无则为 null。 */
	fun getEnclosingNodeUnderCaret(): ICodeNodeRef?

	/** 鼠标下最外层节点；无则为 null。 */
	fun getEnclosingNodeUnderMouse(): ICodeNodeRef?

	/**
	 * 跳转到代码引用。
	 *
	 * @return 是否成功跳转
	 */
	fun open(ref: ICodeNodeRef): Boolean

	/**
	 * 打开某节点的引用/使用对话框。
	 */
	fun openUsageDialog(ref: ICodeNodeRef)

	/**
	 * 重新加载当前标签页的代码。
	 */
	fun reloadActiveTab()

	/**
	 * 重新加载所有已打开标签页的代码。
	 */
	fun reloadAllTabs()

	/**
	 * 保存节点重命名并执行所有必要的 UI 更新。
	 */
	fun applyNodeRename(node: ICodeNodeRef)
}
