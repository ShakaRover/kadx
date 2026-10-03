package jadx.gui.ui.codearea

import jadx.gui.treemodel.JNode
import jadx.gui.treemodel.JResource
import jadx.gui.ui.hexviewer.HexPreviewPanel
import jadx.gui.ui.hexviewer.LazyLoadingBinaryData
import jadx.gui.ui.panel.ILazyLoad
import jadx.gui.ui.tab.TabbedPane
import jadx.gui.utils.UiUtils
import org.exbin.auxiliary.binary_data.BinaryData
import org.exbin.auxiliary.binary_data.array.ByteArrayData
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.nio.charset.StandardCharsets
import javax.swing.border.EmptyBorder

/**
 * 二进制资源内容面板：内部是十六进制预览器 [HexPreviewPanel]。
 *
 * **做什么**：资源文件（图片、字体、未知二进制）用十六进制视图展示。
 * 对于能拿到 zip 条目的资源，使用 [LazyLoadingBinaryData] 惰性读取，避免一次性载入大文件。
 *
 * **为什么 `hexPreviewPanel` 是 transient**：Swing 面板可序列化，但预览器持有流与 UI 资源，
 * 不应被序列化。
 */
class BinaryContentPanel(panel: TabbedPane, jnode: JNode) :
	AbstractCodeContentPanel(panel, jnode),
	ILazyLoad {
	@Transient
	private val hexPreviewPanel: HexPreviewPanel

	init {
		layout = BorderLayout()
		border = EmptyBorder(0, 0, 0, 0)
		hexPreviewPanel = HexPreviewPanel(getSettings())
		hexPreviewPanel.getInspector().setVisible(false)
		add(hexPreviewPanel, BorderLayout.CENTER)
	}

	override fun loadData() {
		loadHexView()
	}

	private fun loadHexView() {
		if (hexPreviewPanel.isDataLoaded()) {
			return
		}
		LOG.debug("Loading Hex View of {}", getNode().getName())
		UiUtils.uiRunAndWait { hexPreviewPanel.setData(getNodeData()) }
	}

	private fun getNodeData(): BinaryData {
		val binaryNode = getNode()
		if (binaryNode is JResource) {
			try {
				val zipEntry = binaryNode.getResFile().getZipEntry()
				if (zipEntry != null) {
					// 需要一个不会被关闭的 InputStream，因此不能使用 ResourcesLoader.decodeStream
					return LazyLoadingBinaryData(zipEntry.getInputStream(), zipEntry.getUncompressedSize())
				}
			} catch (e: Exception) {
				LOG.error("Failed to directly load resource binary data {}: {}", binaryNode.getName(), e.message)
			}
		}
		return ByteArrayData(binaryNode.getCodeInfo().getCodeStr().toByteArray(StandardCharsets.US_ASCII))
	}

	override fun getCodeArea(): AbstractCodeArea? = null

	override fun scrollToPos(pos: Int) {
		UiUtils.uiThreadGuard()
		val bgExec = getMainWindow().getBackgroundExecutor()
		bgExec.startLoading(this::loadHexView) { hexPreviewPanel.scrollToOffset(pos) }
	}

	override fun getChildrenComponent(): Component = hexPreviewPanel

	override fun loadSettings() {
		updateUI()
	}

	override fun dispose() {
		hexPreviewPanel.dispose()
		super.dispose()
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(BinaryContentPanel::class.java)
	}
}
