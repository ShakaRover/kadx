package kadx.gui.treemodel

import kadx.api.ICodeInfo
import kadx.api.JavaNode
import kadx.api.gui.tree.ITreeNode
import kadx.api.metadata.ICodeNodeRef
import kadx.api.resources.ResourceContentType
import kadx.core.utils.ListUtils
import kadx.gui.ui.MainWindow
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.ui.tab.TabbedPane
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import java.util.Comparator
import javax.swing.JPopupMenu
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

/**
 * GUI 树节点的基类。
 *
 * **做什么**：kadx-gui 的类树 / 资源树 / 搜索结果树里所有节点都继承本类。
 * 它在 Swing 的 [DefaultMutableTreeNode] 之上补充了“展示字符串”“图标”“关联的 Java 节点”
 * 以及内容面板等界面语义。
 *
 * **为什么保持这些方法形态**：Swing 的 `JTree` / `TreeModel` 会以虚方法方式调用
 * [getChildAt] / [getChildCount] / [getParent] / [getIndex] 等（由父类提供），
 * 而渲染器会调用 [toString] / [getName] / [makeString] 等。方法名与 JVM 签名必须保持不变，
 * 否则 Java 侧的渲染器与树模型会失效。
 *
 * **为什么不是 `data class`**：树节点存在父子互相引用，且需要按身份比较，
 * 自动生成的 `equals`/`hashCode`/`toString` 会引发递归与错误相等语义。
 */
abstract class JNode :
	DefaultMutableTreeNode(),
	ITreeNode,
	Comparable<JNode> {

	/**
	 * 返回父级 [JClass]（通常是所属的顶层类或内部类）；无父类时返回 `null`。
	 */
	abstract fun getJParent(): JClass?

	/**
	 * 返回顶层 [JClass]；若自身已是顶层则返回自身。
	 * 基类默认没有顶层类，返回 `null`。
	 */
	open fun getRootClass(): JClass? = null

	/**
	 * 关联的 [JavaNode] 视图；基类默认无关联，返回 `null`。
	 */
	open fun getJavaNode(): JavaNode? = null

	override fun getCodeNodeRef(): ICodeNodeRef? = null

	/** 该节点是否有可展示的内容面板。 */
	open fun hasContent(): Boolean = false

	/** 创建内容面板；不支持内容时返回 `null`。 */
	open fun getContentPanel(tabbedPane: TabbedPane): ContentPanel? = null

	/** 代码高亮语法名；无语法时返回 `null`。 */
	open fun getSyntaxName(): String? = SyntaxConstants.SYNTAX_STYLE_NONE

	/** 节点内容（反编译代码或资源文本）。 */
	open fun getCodeInfo(): ICodeInfo = ICodeInfo.EMPTY

	/** 内容类型（文本 / 二进制 / 无）。 */
	open fun getContentType(): ResourceContentType = ResourceContentType.CONTENT_TEXT

	/** 节点内容是否可编辑。 */
	open fun isEditable(): Boolean = false

	/**
	 * 节点名称。
	 *
	 * 无关联 [JavaNode] 时返回 `null`（与原 Java 行为一致，见 [ITreeNode.getName] 的可空声明）。
	 */
	override fun getName(): String? {
		val javaNode = getJavaNode() ?: return null
		return javaNode.getName()
	}

	/** 是否支持以“快速标签页”的方式打开。 */
	open fun supportsQuickTabs(): Boolean = true

	/** 树节点右键菜单；无菜单时返回 `null`。 */
	open fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu? = null

	override fun getID(): String = makeString()

	/** 用于渲染的短名称（树单元格显示）。 */
	abstract fun makeString(): String

	/** HTML 形式的短名称，默认与 [makeString] 相同。 */
	open fun makeStringHtml(): String = makeString()

	/** 附加描述字符串（显示在短名称之后）；无描述时返回 `null`。 */
	open fun makeDescString(): String? = null

	/** 是否存在附加描述字符串。 */
	open fun hasDescString(): Boolean = false

	/** 用于 tooltip / 长名称展示的字符串。 */
	open fun makeLongString(): String = makeString()

	/** HTML 形式的长名称。 */
	open fun makeLongStringHtml(): String = makeLongString()

	/**
	 * 是否禁用 HTML 渲染。
	 * 默认 `true`（按纯文本渲染），需要富文本的节点覆写为 `false`。
	 */
	open fun disableHtml(): Boolean = true

	/**
	 * 节点在反编译代码中的定义位置；无关联节点时返回 `-1`。
	 */
	open fun getPos(): Int {
		val javaNode = getJavaNode() ?: return -1
		return javaNode.getDefPos()
	}

	/** tooltip 文本；默认使用 [makeLongStringHtml]。 */
	open fun getTooltip(): String? = makeLongStringHtml()

	/**
	 * 在直接子节点中查找第一个匹配节点（不递归、不展开）。
	 */
	open fun searchNode(filter: (JNode) -> Boolean): JNode? {
		val en = this.children()
		while (en.hasMoreElements()) {
			val node = en.nextElement() as JNode
			if (filter(node)) {
				return node
			}
		}
		return null
	}

	/**
	 * 按广度优先顺序在整棵子树中查找第一个匹配节点。
	 */
	open fun searchDepthNode(filter: (JNode) -> Boolean): JNode? {
		val en = this.breadthFirstEnumeration()
		while (en.hasMoreElements()) {
			val node = en.nextElement() as JNode
			if (filter(node)) {
				return node
			}
		}
		return null
	}

	/**
	 * 移除并返回第一个匹配的直接子节点。
	 */
	open fun removeNode(filter: (JNode) -> Boolean): JNode? {
		val en = this.children()
		while (en.hasMoreElements()) {
			val node = en.nextElement() as JNode
			if (filter(node)) {
				this.remove(node)
				return node
			}
		}
		return null
	}

	/** 直接子节点列表。 */
	open fun childrenList(): List<TreeNode> = ListUtils.enumerationToList(this.children())

	override fun compareTo(other: JNode): Int = COMPARATOR.compare(this, other)

	override fun toString(): String = makeString()

	companion object {
		private const val serialVersionUID = -5154479091781041008L

		/** 默认排序：先按长名称，再按定义位置。 */
		private val COMPARATOR: Comparator<JNode> = compareBy(
			{ node: JNode -> node.makeLongString() },
			{ node: JNode -> node.getPos() },
		)
	}
}
