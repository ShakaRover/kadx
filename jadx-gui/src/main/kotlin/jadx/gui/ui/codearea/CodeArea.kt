package jadx.gui.ui.codearea

import jadx.api.ICodeInfo
import jadx.api.JavaClass
import jadx.api.JavaNode
import jadx.api.metadata.ICodeAnnotation.AnnType
import jadx.api.metadata.ICodeMetadata
import jadx.gui.JadxWrapper
import jadx.gui.jobs.IBackgroundTask
import jadx.gui.jobs.LoadTask
import jadx.gui.jobs.TaskWithExtraOnFinish
import jadx.gui.settings.JadxProject
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JLoadableNode
import jadx.gui.treemodel.JNode
import jadx.gui.treemodel.JResource
import jadx.gui.ui.MainWindow
import jadx.gui.ui.action.ActionModel
import jadx.gui.ui.action.CommentSearchAction
import jadx.gui.ui.action.CopyReferenceAction
import jadx.gui.ui.action.CopySmaliReferenceAction
import jadx.gui.ui.action.FindUsageAction
import jadx.gui.ui.action.FridaAction
import jadx.gui.ui.action.GoToDeclarationAction
import jadx.gui.ui.action.JsonPrettifyAction
import jadx.gui.ui.action.RenameAction
import jadx.gui.ui.action.ViewCallGraphAction
import jadx.gui.ui.action.ViewClassInheritanceGraphAction
import jadx.gui.ui.action.ViewClassMethodGraphAction
import jadx.gui.ui.action.ViewControlFlowGraphAction
import jadx.gui.ui.action.XposedAction
import jadx.gui.ui.codearea.mode.JCodeMode
import jadx.gui.ui.codearea.sync.CodeAreaSyncee
import jadx.gui.ui.codearea.sync.CodeAreaSyncer
import jadx.gui.ui.codearea.sync.CodeAreaSyncerAbstractFactory
import jadx.gui.ui.codearea.sync.JavaSyncer
import jadx.gui.ui.panel.ContentPanel
import jadx.gui.utils.CaretPositionFix
import jadx.gui.utils.DefaultPopupMenuListener
import jadx.gui.utils.JNodeCache
import jadx.gui.utils.JumpPosition
import jadx.gui.utils.UiUtils
import jadx.gui.utils.shortcut.ShortcutsController
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenTypes
import org.slf4j.LoggerFactory
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent

/**
 * 用于展示 Java 代码与文本类资源（如 AndroidManifest.xml）的 [AbstractCodeArea] 实现。
 *
 * **做什么**：负责 Java 代码区特有的行为：语法 token 修正、Ctrl+点击跳转、
 * 右键菜单动作、类刷新（重命名/注释后）、代码元数据与行号映射查询。
 */
class CodeArea internal constructor(panel: ContentPanel, jnode: JNode) :
	AbstractCodeArea(panel, jnode),
	CodeAreaSyncerAbstractFactory,
	CodeAreaSyncee {

	private var cachedCodeInfo: ICodeInfo? = null
	private var mouseHoverHighlighter: MouseHoverHighlighter? = null
	private val shortcutsController: ShortcutsController
	private var cachedUniqueLineMappings: Map<Int, Int>? = null

	init {
		shortcutsController = mainWindow.getShortcutsController()

		setSyntaxEditingStyle(jnode.getSyntaxName())
		val isJavaCode = isCodeNode
		if (isJavaCode) {
			(getDocument() as RSyntaxDocument).setSyntaxStyle(JadxTokenMaker(this))
		}

		if (jnode is JResource && jnode.makeString().endsWith(".json")) {
			addMenuForJsonFile()
		}

		setHyperlinksEnabled(true)
		setCodeFoldingEnabled(true)
		setLinkScanningMask(InputEvent.CTRL_DOWN_MASK)
		val codeLinkGenerator = CodeLinkGenerator(this)
		setLinkGenerator(codeLinkGenerator)
		addMouseListener(object : MouseAdapter() {
			override fun mouseClicked(e: MouseEvent) {
				if (e.isControlDown || jumpOnDoubleClick(e)) {
					navToDecl(e.getPoint())
				}
			}
		})

		if (isJavaCode) {
			mouseHoverHighlighter = MouseHoverHighlighter(this, codeLinkGenerator)
			addMouseMotionListener(mouseHoverHighlighter)
		}
	}

	override fun loadSettings() {
		super.loadSettings()
		val highlighter = mouseHoverHighlighter
		if (highlighter != null) {
			highlighter.loadSettings()
		}
	}

	val isCodeNode: Boolean get() {
		val n = node
		return n is JClass || n is JCodeMode
	}

	private fun jumpOnDoubleClick(e: MouseEvent): Boolean = e.getClickCount() == 2 && mainWindow.getSettings().isJumpOnDoubleClick

	private fun navToDecl(point: Point) {
		val offs = viewToModel2D(point)
		val n = getJNodeAtOffset(adjustOffsetForWordToken(offs))
		if (n != null) {
			getContentPanel().tabsController.codeJump(n)
		}
	}

	override fun getCodeInfo(): ICodeInfo {
		var info = cachedCodeInfo
		if (info == null) {
			val n = node
			if (n == null) {
				LOG.debug("CodeArea used after dispose!")
				return ICodeInfo.EMPTY
			}
			info = checkNotNull(n.getCodeInfo())
			cachedCodeInfo = info
		}
		return info
	}

	override fun getLoadTask(): IBackgroundTask {
		val n = node
		if (n is JLoadableNode) {
			val loadTask = n.getLoadTask()
			if (loadTask != null) {
				return TaskWithExtraOnFinish(
					loadTask,
					Runnable {
						setText(getCodeInfo().codeStr)
						setCaretPosition(0)
						setLoaded()
					},
				)
			}
		}
		return LoadTask<String>({ getCodeInfo().codeStr }) { code ->
			setText(code)
			setCaretPosition(0)
			setLoaded()
		}
	}

	override fun refresh() {
		cachedCodeInfo = null
		setText(getCodeInfo().codeStr)
	}

	override fun createPopupMenu(): JPopupMenu {
		val popup = super.createPopupMenu()
		if (node is JClass) {
			appendCodeMenuItems(popup)
		}
		return popup
	}

	private fun appendCodeMenuItems(popupMenu: JPopupMenu) {
		val shortcutsController = mainWindow.getShortcutsController()
		val popup = JNodePopupBuilder(this, popupMenu, shortcutsController)
		popup.addSeparator()
		popup.add(FindUsageAction(this))
		popup.add(UsageDialogPlusAction(this))
		popup.add(GoToDeclarationAction(this))
		popup.add(CommentAction(this))
		popup.add(CommentSearchAction(this))
		popup.add(RenameAction(this))
		popup.add(CopyReferenceAction(this))
		popup.add(CopySmaliReferenceAction(this))
		popup.addSeparator()
		popup.add(FridaAction(this))
		popup.add(XposedAction(this))
		popup.addSeparator()
		popup.add(ViewClassInheritanceGraphAction(this))
		popup.add(ViewClassMethodGraphAction(this))
		popup.add(ViewCallGraphAction(this))
		popup.add(ViewControlFlowGraphAction(ActionModel.VIEW_CONTROL_FLOW_GRAPH, this))
		popup.addSeparator()
		popup.add(ConvertNumberAction(this))

		mainWindow.getGuiPluginsManager().getPluginsContext().appendPopupMenus(this, popup)

		// 鼠标右键点击时移动光标
		popupMenu.addPopupMenuListener(object : DefaultPopupMenuListener {
			override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
				val codeArea = this@CodeArea
				if (codeArea.getSelectedText() == null) {
					val offset = UiUtils.getOffsetAtMousePosition(codeArea)
					if (offset >= 0) {
						codeArea.setCaretPosition(offset)
					}
				}
			}
		})
	}

	private fun addMenuForJsonFile() {
		val shortcutsController = mainWindow.getShortcutsController()
		val popup = JNodePopupBuilder(this, getPopupMenu(), shortcutsController)
		popup.addSeparator()
		popup.add(JsonPrettifyAction(this))
	}

	/**
	 * 在指定偏移处查找单词 token 的起始位置。
	 *
	 * @return 未找到单词 token 时返回 -1
	 */
	fun adjustOffsetForWordToken(offset: Int): Int {
		val token: Token = getWordTokenAtOffset(offset) ?: return -1
		val type = token.getType()
		if (isCodeNode) {
			if (type == TokenTypes.IDENTIFIER || type == TokenTypes.FUNCTION) {
				return token.getOffset()
			}
			if (type == TokenTypes.ANNOTATION && token.length() > 1) {
				return token.getOffset() + 1
			}
			if (type == TokenTypes.RESERVED_WORD && token.length() == 6 && token.getLexeme() == "static") {
				// 可能是类初始化方法
				return token.getOffset()
			}
		} else if (type == TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE) {
			return token.getOffset() + 1 // 跳过开头的引号 (
		}
		return -1
	}

	/**
	 * 在代码中按偏移查找节点，并返回其定义位置（用于从使用处跳转）。
	 */
	fun getDefPosForNodeAtOffset(offset: Int): JumpPosition? {
		if (offset == -1) {
			return null
		}
		val foundNode = getJavaNodeAtOffset(offset) ?: return null
		if (foundNode === getNode().getJavaNode()) {
			// 当前节点
			return JumpPosition(getNode())
		}
		val jNode = convertJavaNode(foundNode) ?: return null
		return JumpPosition(jNode)
	}

	private fun convertJavaNode(javaNode: JavaNode): JNode? {
		val nodeCache: JNodeCache = mainWindow.getCacheObject().nodeCache
		return nodeCache.makeFrom(javaNode)
	}

	val nodeUnderCaret: JNode? get() {
		val caretPos = getCaretPosition()
		return getJNodeAtOffset(adjustOffsetForWordToken(caretPos))
	}

	val enclosingNodeUnderCaret: JNode? get() {
		val caretPos = getCaretPosition()
		var start = adjustOffsetForWordToken(caretPos)
		if (start == -1) {
			start = caretPos
		}
		return getEnclosingJNodeAtOffset(start)
	}

	val nodeUnderMouse: JNode? get() {
		val pos = UiUtils.getMousePosition(this)
		return getJNodeAtOffset(adjustOffsetForWordToken(viewToModel2D(pos)))
	}

	val enclosingNodeUnderMouse: JNode? get() {
		val pos = UiUtils.getMousePosition(this)
		return getEnclosingJNodeAtOffset(adjustOffsetForWordToken(viewToModel2D(pos)))
	}

	fun getEnclosingJNodeAtOffset(offset: Int): JNode? {
		val javaNode = getEnclosingJavaNode(offset)
		if (javaNode != null) {
			return convertJavaNode(javaNode)
		}
		return null
	}

	fun getJNodeAtOffset(offset: Int): JNode? {
		val javaNode = getJavaNodeAtOffset(offset)
		if (javaNode != null) {
			return convertJavaNode(javaNode)
		}
		return null
	}

	/** 在代码中按偏移查找被引用的 Java 节点。 */
	fun getJavaNodeAtOffset(offset: Int): JavaNode? {
		if (offset == -1) {
			return null
		}
		try {
			return jadxWrapper.getDecompiler().getJavaNodeAtPosition(getCodeInfo(), offset)
		} catch (e: Exception) {
			LOG.error("Can't get java node by offset: {}", offset, e)
		}
		return null
	}

	fun getClosestJavaNode(offset: Int): JavaNode? {
		if (offset == -1) {
			return null
		}
		try {
			return jadxWrapper.getDecompiler().getClosestJavaNode(getCodeInfo(), offset)
		} catch (e: Exception) {
			LOG.error("Can't get java node by offset: {}", offset, e)
			return null
		}
	}

	fun getEnclosingJavaNode(offset: Int): JavaNode? {
		if (offset == -1) {
			return null
		}
		try {
			return jadxWrapper.getDecompiler().getEnclosingNode(getCodeInfo(), offset)
		} catch (e: Exception) {
			LOG.error("Can't get java node by offset: {}", offset, e)
			return null
		}
	}

	fun getJavaClassIfAtPos(pos: Int): JavaClass? {
		try {
			val codeInfo = getCodeInfo()
			if (!codeInfo.hasMetadata()) {
				return null
			}
			val ann = codeInfo.codeMetadata.getAt(pos) ?: return null
			return when (ann.annType) {
				AnnType.CLASS ->
					jadxWrapper.getDecompiler().getJavaNodeByCodeAnnotation(codeInfo, ann) as? JavaClass

				AnnType.METHOD -> {
					// 使用构造调用处的类
					val node = jadxWrapper.getDecompiler().getJavaNodeByCodeAnnotation(codeInfo, ann)
					node?.declaringClass
				}

				else -> null
			}
		} catch (e: Exception) {
			LOG.error("Can't get java node by offset: {}", pos, e)
			return null
		}
	}

	fun refreshClass() {
		refreshClass(false)
	}

	fun refreshClass(alreadyReloaded: Boolean) {
		val n = node
		if (n is JClass) {
			val cls = n.getRootClass()
			try {
				val caretFix = CaretPositionFix(this)
				caretFix.save()

				cachedCodeInfo = if (alreadyReloaded) {
					cls.getCodeInfo()
				} else {
					// 不好：会在 UI 线程阻塞进行可能很耗时的反编译
					cls.reload(mainWindow.getCacheObject())
				}

				val codeContentPanel = getContentPanel() as ClassCodeContentPanel
				codeContentPanel.getTabbedPane().refresh(cls)
				codeContentPanel.getJavaCodePanel().refresh(caretFix)
			} catch (e: Exception) {
				LOG.error("Failed to reload class: {}", cls.fullName, e)
			}
		}
	}

	/**
	 * 在后台刷新类，反编译完成后回到 UI 线程更新。应从 UI 线程调用。
	 */
	fun backgroundRefreshClass() {
		UiUtils.uiThreadGuard()
		mainWindow.getBackgroundExecutor().execute("Refreshing...") {
			checkNotNull(getNode().getRootClass()).reload(mainWindow.getCacheObject())
			UiUtils.uiRunAndWait {
				refreshClass(true)
			}
		}
	}

	val mainWindow: MainWindow get() = getContentPanel().mainWindow

	val jadxWrapper: JadxWrapper get() = mainWindow.getWrapper()

	val project: JadxProject get() = mainWindow.getProject()

	override fun dispose() {
		shortcutsController.unbindActionsForComponent(this)

		super.dispose()
		cachedCodeInfo = null
	}

	override fun createCodeAreaSyncer(): CodeAreaSyncer = JavaSyncer(this)

	override fun sync(codeAreaSyncer: CodeAreaSyncer): Boolean = codeAreaSyncer.syncTo(this)

	val codeMetadata: ICodeMetadata? get() {
		val codeInfo = getCodeInfo()
		if (!codeInfo.hasMetadata()) {
			LOG.warn("No code info metadata for {}", codeInfo)
			return null
		}
		return codeInfo.codeMetadata
	}

	/**
	 * 返回“反编译输出行号 -> dex 调试行号”的映射。
	 * 这里用的是从 1 开始的行号，不是 CodeArea 的行索引。
	 */
	val lineMappings: Map<Int, Int> get() {
		val codeInfo = getCodeInfo()
		if (!codeInfo.hasMetadata()) {
			LOG.debug("No code info metadata for {}", codeInfo)
			return emptyMap()
		}
		val lineMapping = codeInfo.codeMetadata.getLineMapping()
		if (lineMapping.isEmpty()) {
			LOG.debug("Line mappings are empty for {}", codeInfo)
			return emptyMap()
		}
		return lineMapping
	}

	/**
	 * 与 [getLineMappings] 相同，但仅在每个 dex 调试行号只出现一次时可用。
	 * 若某个值出现多次，说明多个方法可能共享调试行号，此时不能用于代码同步。
	 */
	val functionUniqueLineMappings: Map<Int, Int> get() {
		var mappings = cachedUniqueLineMappings
		if (mappings == null) {
			mappings = calcUniqueLineMappings()
			cachedUniqueLineMappings = mappings
		}
		return mappings
	}

	private fun calcUniqueLineMappings(): Map<Int, Int> {
		val lineMappings = lineMappings
		val isAnyRepeated = lineMappings.values
			.groupingBy { it }
			.eachCount()
			.values
			.any { it > 1 }
		if (isAnyRepeated) {
			LOG.debug("Dex debug line mappings are not unique")
			return emptyMap()
		}
		return lineMappings
	}

	companion object {
		private const val serialVersionUID = 6312736869579635796L

		private val LOG = LoggerFactory.getLogger(CodeArea::class.java)
	}
}
