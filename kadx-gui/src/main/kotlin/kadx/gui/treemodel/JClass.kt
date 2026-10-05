package kadx.gui.treemodel

import kadx.api.ICodeInfo
import kadx.api.JavaClass
import kadx.api.JavaField
import kadx.api.JavaMethod
import kadx.api.JavaNode
import kadx.api.data.ICodeRename
import kadx.api.data.impl.KadxCodeRename
import kadx.api.data.impl.KadxNodeRef
import kadx.core.deobf.NameMapper
import kadx.core.dex.attributes.AFlag
import kadx.core.dex.info.AccessInfo
import kadx.core.dex.nodes.ICodeNode
import kadx.gui.jobs.SimpleTask
import kadx.gui.ui.MainWindow
import kadx.gui.ui.codearea.ClassCodeContentPanel
import kadx.gui.ui.panel.ContentPanel
import kadx.gui.ui.popupmenu.JClassPopupMenu
import kadx.gui.ui.tab.TabbedPane
import kadx.gui.utils.CacheObject
import kadx.gui.utils.Icons
import kadx.gui.utils.JNodeCache
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JPopupMenu

/**
 * 类节点。
 *
 * **做什么**：类树的核心节点，包装一个 [JavaClass]，负责在展开时加载内部类 / 字段 / 方法，
 * 并提供重命名、跳转、图标、右键菜单等能力。
 *
 * **为什么不是 `data class`**：节点相等性委托给 `cls`，且树中存在父子互相引用，
 * 自动生成的 `equals`/`hashCode`/`toString` 会引发递归与错误相等语义。
 */
class JClass(
	private val cls: JavaClass,
	private val jParent: JClass?,
	private val nodeCache: JNodeCache,
) : JLoadableNode(),
	JRenameNode {

	/** 内部类 / 字段 / 方法是否已加载。 */
	private var loaded: Boolean = jParent != null

	fun getCls(): JavaClass = cls

	override fun canRename(): Boolean = !cls.getClassNode().contains(AFlag.DONT_RENAME)

	override fun loadNode() {
		getRootClass().load()
	}

	@Synchronized
	override fun getLoadTask(): SimpleTask? {
		if (loaded) {
			return null
		}
		val rootClass = getRootClass()
		return SimpleTask(
			NLS.str("progress.decompile"),
			Runnable { rootClass.getCls().getClassNode().decompile() }, // 后台线程执行反编译
			Runnable { rootClass.load() }, // 回到 UI 线程加载类内部信息并刷新
		)
	}

	@Synchronized
	private fun load() {
		if (loaded) {
			return
		}
		cls.decompile()
		loaded = true
		update()
	}

	@Synchronized
	fun reload(cache: CacheObject): ICodeInfo {
		cache.nodeCache.removeWholeClass(cls)
		val codeInfo = cls.reload()
		loaded = true
		update()
		return codeInfo
	}

	@Synchronized
	fun unload(cache: CacheObject) {
		cache.nodeCache.removeWholeClass(cls)
		cls.unload()
		loaded = false
	}

	@Synchronized
	fun update() {
		removeAllChildren()
		if (!loaded) {
			add(TextNode(NLS.str("tree.loading")))
		} else {
			for (javaClass in cls.getInnerClasses()) {
				val innerCls = checkNotNull(nodeCache.makeFrom(javaClass))
				add(innerCls)
				innerCls.update()
			}
			for (f in cls.getFields()) {
				add(checkNotNull(nodeCache.makeFrom(f)))
			}
			for (m in cls.getMethods()) {
				add(checkNotNull(nodeCache.makeFrom(m)))
			}
		}
	}

	override fun getCodeInfo(): ICodeInfo = cls.getCodeInfo()

	override fun hasContent(): Boolean = true

	override fun getContentPanel(tabbedPane: TabbedPane): ContentPanel = ClassCodeContentPanel(tabbedPane, this)

	val smali: String get() = cls.getSmali()

	override fun getSyntaxName(): String = SyntaxConstants.SYNTAX_STYLE_JAVA

	override fun onTreePopupMenu(mainWindow: MainWindow): JPopupMenu = JClassPopupMenu(mainWindow, this)

	override fun getJavaNode(): JavaNode = cls

	override fun getCodeNodeRef(): ICodeNode = cls.getClassNode()

	override fun getJParent(): JClass? = jParent

	override fun getRootClass(): JClass {
		val parent = jParent ?: return this
		return parent.getRootClass()
	}

	override fun getName(): String = cls.getName()

	val fullName: String get() = cls.getFullName()

	override fun getTitle(): String = makeLongStringHtml()

	override fun getIcon(): Icon {
		val accessInfo: AccessInfo = cls.getAccessInfo()
		if (accessInfo.isEnum()) {
			return ICON_ENUM
		}
		if (accessInfo.isAnnotation()) {
			return ICON_ANNOTATION
		}
		if (accessInfo.isInterface()) {
			return ICON_INTERFACE
		}
		if (accessInfo.isAbstract()) {
			return ICON_CLASS_ABSTRACT
		}
		if (accessInfo.isProtected()) {
			return ICON_CLASS_PROTECTED
		}
		if (accessInfo.isPrivate()) {
			return ICON_CLASS_PRIVATE
		}
		if (accessInfo.isPublic()) {
			return ICON_CLASS_PUBLIC
		}
		return Icons.CLASS
	}

	override fun isValidName(newName: String): Boolean {
		if (NameMapper.isValidIdentifier(newName)) {
			return true
		}
		if (cls.isInner()) {
			// 内部类不允许修改包名
			return false
		}
		if (NameMapper.isValidFullIdentifier(newName)) {
			return true
		}
		// 移动到默认包
		return newName.startsWith(".") && NameMapper.isValidIdentifier(newName.substring(1))
	}

	override fun buildCodeRename(newName: String, renames: MutableSet<ICodeRename>): ICodeRename = KadxCodeRename(KadxNodeRef.forCls(cls), newName)

	override fun removeAlias() {
		// 只重置短名称，包名需要通过 PackageNode 显式重置
		cls.getClassNode().rename("")
	}

	override fun addUpdateNodes(toUpdate: MutableList<JavaNode>) {
		toUpdate.add(cls)
		toUpdate.addAll(cls.useIn)
	}

	override fun reload(mainWindow: MainWindow) {
		// TODO: 仅在类的包名变化时才重建包结构
		mainWindow.reloadTreePreservingState()
	}

	override fun hashCode(): Int = cls.hashCode()

	override fun equals(other: Any?): Boolean = this === other || (other is JClass && cls == other.cls)

	override fun makeString(): String = cls.getName()

	override fun makeLongString(): String = cls.getFullName()

	fun compareToCls(otherCls: JClass): Int = this.getCls().getRawName().compareTo(otherCls.getCls().getRawName())

	override fun compareTo(other: JNode): Int {
		if (other is JClass) {
			return compareToCls(other)
		}
		if (other is JMethod) {
			val cmp = compareToCls(other.getJParent())
			if (cmp != 0) {
				return cmp
			}
			return -1
		}
		return super.compareTo(other)
	}

	companion object {
		private const val serialVersionUID = -1239986875244097177L

		private val ICON_CLASS_ABSTRACT: ImageIcon = UiUtils.openSvgIcon("nodes/abstractClass")
		private val ICON_CLASS_PUBLIC: ImageIcon = UiUtils.openSvgIcon("nodes/publicClass")
		private val ICON_CLASS_PRIVATE: ImageIcon = UiUtils.openSvgIcon("nodes/privateClass")
		private val ICON_CLASS_PROTECTED: ImageIcon = UiUtils.openSvgIcon("nodes/protectedClass")
		private val ICON_INTERFACE: ImageIcon = UiUtils.openSvgIcon("nodes/interface")
		private val ICON_ENUM: ImageIcon = UiUtils.openSvgIcon("nodes/enum")
		private val ICON_ANNOTATION: ImageIcon = UiUtils.openSvgIcon("nodes/annotationtype")
	}
}
