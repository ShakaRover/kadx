package jadx.gui.settings

import jadx.api.JavaClass
import jadx.gui.plugins.mappings.JInputMapping
import jadx.gui.settings.data.ITabStatePersist
import jadx.gui.settings.data.TabViewState
import jadx.gui.settings.data.ViewPoint
import jadx.gui.treemodel.JClass
import jadx.gui.treemodel.JNode
import jadx.gui.treemodel.JResource
import jadx.gui.treemodel.JSubResource
import jadx.gui.ui.MainWindow
import jadx.gui.ui.codearea.EditorViewState
import jadx.gui.utils.UiUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 标签页状态与 [TabViewState] 之间的转换器。
 *
 * **做什么**：保存项目时把 [EditorViewState] 转成可序列化的 [TabViewState]，
 * 加载项目时再按类型还原；对于内置节点（类、资源、子资源、映射）直接处理，
 * 其余节点交给插件注册的 [ITabStatePersist] 适配器。
 */
class TabStateViewAdapter {

	private val customAdaptersMap: MutableMap<String, ITabStatePersist> = HashMap()

	fun build(viewState: EditorViewState): TabViewState? {
		val tvs = TabViewState()
		tvs.setSubPath(viewState.getSubPath())
		if (!saveJNode(tvs, viewState.getNode())) {
			if (UiUtils.JADX_GUI_DEBUG) {
				LOG.warn("Can't save view state: {}", viewState)
			}
			return null
		}
		tvs.setCaret(viewState.getCaretPos())
		tvs.setView(ViewPoint(viewState.getViewPoint()))
		tvs.setActive(viewState.isActive)
		tvs.setPinned(viewState.isPinned)
		tvs.setBookmarked(viewState.isBookmarked)
		tvs.setHidden(viewState.isHidden)
		tvs.setPreviewTab(viewState.isPreviewTab)
		return tvs
	}

	fun load(mw: MainWindow, tvs: TabViewState): EditorViewState? {
		try {
			val node = loadJNode(mw, tvs)
			if (node == null) {
				if (UiUtils.JADX_GUI_DEBUG) {
					LOG.warn("Can't restore view for {}", tvs)
				}
				return null
			}
			val view = checkNotNull(tvs.getView()) { "View is not set for: $tvs" }
			val viewState = EditorViewState(node, tvs.getSubPath() ?: "", tvs.getCaret(), view.toPoint())
			viewState.setActive(tvs.isActive)
			viewState.setPinned(tvs.isPinned)
			viewState.setBookmarked(tvs.isBookmarked)
			viewState.setHidden(tvs.isHidden)
			viewState.setPreviewTab(tvs.isPreviewTab)
			return viewState
		} catch (e: Exception) {
			LOG.error("Failed to load tab state: {}", tvs, e)
			return null
		}
	}

	fun setCustomAdapters(customAdapters: List<ITabStatePersist>) {
		customAdaptersMap.clear()
		for (customAdapter in customAdapters) {
			customAdaptersMap[customAdapter.getNodeClass().name] = customAdapter
		}
	}

	private fun loadJNode(mw: MainWindow, tvs: TabViewState): JNode? {
		when (tvs.getType()) {
			"class" -> {
				val javaClass: JavaClass? = mw.getWrapper().searchJavaClassByRawName(checkNotNull(tvs.getTabPath()))
				if (javaClass != null) {
					return mw.getCacheObject().nodeCache.makeFrom(javaClass)
				}
			}

			"resource" -> return mw.getTreeRoot().searchResourceByName(checkNotNull(tvs.getTabPath()))

			"sub-resource" -> {
				val parts = checkNotNull(tvs.getTabPath()).split(JSubResource.SUB_RES_PREFIX)
				val baseRes = mw.getTreeRoot().searchResourceByName(parts[0])
				if (baseRes != null) {
					val subName = parts[1]
					// 搜索前会先加载子节点
					return baseRes.searchDepthNode { n -> n.getName() == subName }
				}
				return null
			}

			"mapping" -> return mw.getTreeRoot().followStaticPath("JInputs").searchNode { node -> node is JInputMapping }
		}
		val statePersist = customAdaptersMap[tvs.getType()]
		if (statePersist != null) {
			try {
				return statePersist.load(checkNotNull(tvs.getTabPath()))
			} catch (e: Exception) {
				LOG.error("Failed to restore tab for custom node adapter: {}", tvs.getType(), e)
			}
		}
		return null
	}

	private fun saveJNode(tvs: TabViewState, node: JNode): Boolean {
		if (node is JClass) {
			tvs.setType("class")
			tvs.setTabPath(node.getCls().getRawName())
			return true
		}
		if (node is JSubResource) {
			tvs.setType("sub-resource")
			tvs.setTabPath(node.getBaseRes().getName() + JSubResource.SUB_RES_PREFIX + node.getName())
			return true
		}
		if (node is JResource) {
			tvs.setType("resource")
			tvs.setTabPath(node.getName())
			return true
		}
		if (node is JInputMapping) {
			tvs.setType("mapping")
			return true
		}

		val typeName = node.javaClass.name
		val statePersist = customAdaptersMap[typeName]
		if (statePersist != null) {
			try {
				tvs.setTabPath(statePersist.save(node))
				tvs.setType(statePersist.getNodeClass().name)
				return true
			} catch (e: Exception) {
				LOG.error("Failed to save state for custom node: {}", typeName, e)
			}
		}
		return false
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(TabStateViewAdapter::class.java)
	}
}
