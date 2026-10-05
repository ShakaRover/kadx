package kadx.gui.settings.ui.plugins

import kadx.api.plugins.gui.ISettingsGroup
import kadx.core.plugins.PluginRuntime
import kadx.core.utils.StringUtils
import kadx.core.utils.Utils
import kadx.gui.ui.MainWindow
import kadx.gui.utils.Link
import kadx.gui.utils.NLS
import kadx.gui.utils.UiUtils
import kadx.plugins.tools.KadxPluginsList
import kadx.plugins.tools.KadxPluginsTools
import kadx.plugins.tools.data.KadxPluginMetadata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

/**
 * 插件设置主页：左侧插件列表（已安装 / 可用 / 内置），右侧详情面板。
 *
 * **做什么**：异步下载可用插件列表并合并到模型中；选中条目时展示描述、主页链接，
 * 并提供安装 / 卸载 / 启用禁用等操作按钮。
 */
internal class PluginSettingsGroup(
	private val pluginsSettings: PluginSettings,
	private val mainWindow: MainWindow,
	private val collectedPlugins: List<PluginRuntime>,
) : ISettingsGroup {

	private val title: String = NLS.str("preferences.plugins")
	private val subGroups: MutableList<ISettingsGroup> = ArrayList()
	private lateinit var detailsPanel: JPanel

	override fun getTitle(): String = title

	override fun getSubGroups(): MutableList<ISettingsGroup> = subGroups

	override fun buildComponent(): JComponent {
		// 懒加载主页面
		return buildMainSettingsPage()
	}

	override fun close(save: Boolean) {
		subGroups.forEach { it.close(save) }
	}

	private fun buildMainSettingsPage(): JPanel {
		val installPluginBtn = JButton(NLS.str("preferences.plugins.install"))
		installPluginBtn.addActionListener { pluginsSettings.addPlugin() }

		val updateAllBtn = JButton(NLS.str("preferences.plugins.update_all"))
		updateAllBtn.addActionListener { pluginsSettings.updateAll() }

		val actionsPanel = JPanel()
		actionsPanel.layout = BoxLayout(actionsPanel, BoxLayout.LINE_AXIS)
		actionsPanel.add(installPluginBtn)
		actionsPanel.add(Box.createRigidArea(Dimension(5, 0)))
		actionsPanel.add(updateAllBtn)

		val listModel = DefaultListModel<BasePluginListNode>()
		val pluginList = JList(listModel)
		pluginList.selectionMode = ListSelectionModel.SINGLE_SELECTION
		pluginList.cellRenderer = PluginsListCellRenderer()
		pluginList.addListSelectionListener { onSelection(pluginList.selectedValue) }
		pluginList.isFocusable = true

		val scrollPane = JScrollPane(pluginList)
		scrollPane.minimumSize = Dimension(80, 120)

		detailsPanel = JPanel(BorderLayout(5, 5))
		detailsPanel.border = BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder(NLS.str("preferences.plugins.details")),
			BorderFactory.createEmptyBorder(10, 10, 10, 10),
		)
		detailsPanel.layout = BoxLayout(detailsPanel, BoxLayout.PAGE_AXIS)

		val splitPanel = JSplitPane()
		splitPanel.border = BorderFactory.createEmptyBorder(10, 2, 2, 2)
		splitPanel.leftComponent = scrollPane
		splitPanel.rightComponent = detailsPanel

		val mainPanel = JPanel()
		mainPanel.layout = BorderLayout(5, 5)
		mainPanel.border = BorderFactory.createTitledBorder(title)
		mainPanel.add(actionsPanel, BorderLayout.PAGE_START)
		mainPanel.add(splitPanel, BorderLayout.CENTER)

		applyData(listModel)
		return mainPanel
	}

	private fun applyData(listModel: DefaultListModel<BasePluginListNode>) {
		val installed = KadxPluginsTools.instance.getInstalled()
		val nodes = ArrayList<BasePluginListNode>(installed.size + collectedPlugins.size)
		val installedSet = HashSet<String?>(installed.size)
		for (pluginMetadata in installed) {
			installedSet.add(pluginMetadata.pluginId)
			nodes.add(InstalledPluginNode(pluginMetadata))
		}
		for (plugin in collectedPlugins) {
			if (!installedSet.contains(plugin.pluginId)) {
				nodes.add(LoadedPluginNode(plugin))
			}
		}
		nodes.sortBy { it.getTitle() }

		fillListModel(listModel, nodes, emptyList())
		loadAvailablePlugins(listModel, nodes, installedSet)
	}

	private fun fillListModel(
		listModel: DefaultListModel<BasePluginListNode>,
		nodes: List<BasePluginListNode>,
		available: List<AvailablePluginNode>,
	) {
		listModel.clear()
		listModel.addElement(TitleNode("Installed"))
		nodes.filter { it.getAction() == PluginAction.UNINSTALL }.forEach { listModel.addElement(it) }
		listModel.addElement(TitleNode("Available"))
		listModel.addAll(available)
		listModel.addElement(TitleNode("Bundled"))
		nodes.filter { it.getAction() == PluginAction.NONE }.forEach { listModel.addElement(it) }
	}

	private fun loadAvailablePlugins(
		listModel: DefaultListModel<BasePluginListNode>,
		nodes: List<BasePluginListNode>,
		installedSet: Set<String?>,
	) {
		mainWindow.getBackgroundExecutor().execute(NLS.str("preferences.plugins.task.downloading_list")) {
			try {
				KadxPluginsList.instance.get { availablePlugins ->
					val availableNodes = availablePlugins
						.filter { !installedSet.contains(it.pluginId) }
						.map { AvailablePluginNode(it) }
					UiUtils.uiRunAndWait { fillListModel(listModel, nodes, availableNodes) }
				}
			} catch (e: Exception) {
				LOG.warn("Failed to load available plugins list", e)
			}
		}
	}

	private fun onSelection(node: BasePluginListNode) {
		detailsPanel.removeAll()
		if (node.hasDetails()) {
			val nameLbl = JLabel(node.getTitle())
			val baseFont = nameLbl.font
			nameLbl.font = baseFont.deriveFont(Font.BOLD, baseFont.size2D + 2)

			var homeLink: Link? = null
			val homepage = node.getHomepage()
			if (homepage != null && StringUtils.notBlank(homepage)) {
				homeLink = Link("Homepage: $homepage", homepage)
				homeLink.horizontalAlignment = SwingConstants.LEFT
			}

			val descArea = JTextPane()
			descArea.text = node.getDescription()
			descArea.font = baseFont.deriveFont(baseFont.size2D + 1)
			descArea.isEditable = false
			descArea.border = BorderFactory.createEmptyBorder()
			descArea.isOpaque = true

			val top = JPanel()
			top.layout = BoxLayout(top, BoxLayout.LINE_AXIS)
			top.border = BorderFactory.createEmptyBorder(10, 2, 10, 2)
			top.add(nameLbl)
			top.add(Box.createHorizontalGlue())
			val actionBtn = makeActionButton(node)
			if (actionBtn != null) {
				top.add(actionBtn)
			}
			if (node.getAction() == PluginAction.UNINSTALL) {
				// TODO: 允许禁用内置插件
				val disabled = node.isDisabled()
				val statusChangeLabel = if (disabled) {
					NLS.str("preferences.plugins.enable_btn")
				} else {
					NLS.str("preferences.plugins.disable_btn")
				}
				val statusBtn = JButton(statusChangeLabel)
				statusBtn.addActionListener {
					pluginsSettings.changeDisableStatus(checkNotNull(node.getPluginId()), !disabled)
				}
				top.add(Box.createHorizontalStrut(10))
				top.add(statusBtn)
			}

			val center = JPanel()
			center.layout = BoxLayout(center, BoxLayout.PAGE_AXIS)
			center.border = BorderFactory.createEmptyBorder(10, 2, 10, 2)
			center.add(descArea)
			if (homeLink != null) {
				val link = JPanel()
				link.layout = BoxLayout(link, BoxLayout.LINE_AXIS)
				link.add(homeLink)
				link.add(Box.createHorizontalGlue())
				center.add(link)
			}
			center.add(Box.createVerticalGlue())

			detailsPanel.add(top, BorderLayout.PAGE_START)
			detailsPanel.add(center, BorderLayout.CENTER)
		}
		detailsPanel.updateUI()
	}

	private fun makeActionButton(node: BasePluginListNode): JButton? = when (node.getAction()) {
		PluginAction.NONE -> null

		PluginAction.INSTALL -> {
			val installBtn = JButton(NLS.str("preferences.plugins.install_btn"))
			installBtn.addActionListener { pluginsSettings.install(checkNotNull(node.getLocationId())) }
			installBtn
		}

		PluginAction.UNINSTALL -> {
			val uninstallBtn = JButton(NLS.str("preferences.plugins.uninstall_btn"))
			uninstallBtn.addActionListener { pluginsSettings.uninstall(checkNotNull(node.getPluginId())) }
			uninstallBtn
		}
	}

	private class PluginsListCellRenderer : ListCellRenderer<BasePluginListNode> {
		private val panel: JPanel
		private val nameLbl: JLabel
		private val versionLbl: JLabel
		private val titleLbl: JLabel

		init {
			panel = JPanel()
			panel.isOpaque = true
			panel.layout = BoxLayout(panel, BoxLayout.LINE_AXIS)
			panel.border = BorderFactory.createEmptyBorder(2, 10, 2, 10)

			nameLbl = JLabel("")
			nameLbl.font = nameLbl.font.deriveFont(Font.BOLD)
			nameLbl.isOpaque = true
			versionLbl = JLabel("")
			versionLbl.isOpaque = true
			versionLbl.preferredSize = Dimension(40, 10)

			panel.add(nameLbl)
			panel.add(Box.createHorizontalStrut(20))
			panel.add(Box.createHorizontalGlue())
			panel.add(versionLbl)
			panel.add(Box.createHorizontalStrut(10))

			titleLbl = JLabel()
			titleLbl.horizontalAlignment = SwingConstants.CENTER
			titleLbl.preferredSize = Dimension(40, 10)
		}

		override fun getListCellRendererComponent(
			list: JList<out BasePluginListNode>,
			plugin: BasePluginListNode,
			index: Int,
			isSelected: Boolean,
			cellHasFocus: Boolean,
		): Component {
			if (!plugin.hasDetails()) {
				titleLbl.text = plugin.getTitle()
				return titleLbl
			}
			nameLbl.text = plugin.getTitle()
			nameLbl.toolTipText = plugin.getLocationId()
			versionLbl.text = Utils.getOrElse(plugin.getVersion(), "")
			panel.accessibleContext.accessibleName = plugin.getTitle()

			val enabled = !plugin.isDisabled()
			nameLbl.isEnabled = enabled
			versionLbl.isEnabled = enabled

			if (isSelected) {
				panel.background = list.selectionBackground
				nameLbl.background = list.selectionBackground
				nameLbl.foreground = list.selectionForeground
				versionLbl.background = list.selectionBackground
			} else {
				panel.background = list.background
				nameLbl.background = list.background
				nameLbl.foreground = list.foreground
				versionLbl.background = list.background
			}
			return panel
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(PluginSettingsGroup::class.java)
	}
}
