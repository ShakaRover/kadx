package kadx.gui.settings.data

import kadx.api.data.impl.KadxCodeData
import kadx.gui.search.providers.ResourceFilter
import java.nio.file.Path
import java.util.Collections

/**
 * 项目文件（`.kadx`）的持久化数据。
 *
 * **做什么**：保存项目包含的输入文件、类树展开状态、代码注释/重命名数据、打开的标签页、
 * 映射文件路径、缓存目录、搜索历史、插件选项等。由 Gson 序列化到项目文件中。
 *
 * **为什么保留显式 getter/setter 与原始字段名**：字段名即项目 JSON 的键，不能改动；
 * 同时 `CacheManager`（尚未迁移的 Java）仍按 `getCacheDir()` 等命名访问，需保留方法名。
 */
class ProjectData {

	private var projectVersion: Int = 2
	private var files: List<Path> = ArrayList()
	private var treeExpansionsV2: List<String> = ArrayList()
	private var codeData: KadxCodeData = KadxCodeData()
	private var openTabs: List<TabViewState> = Collections.emptyList()
	private var mappingsPath: Path? = null
	private var cacheDir: String? = null // 不要使用相对路径适配器
	private var enableLiveReload: Boolean = false
	private var searchHistory: MutableList<String> = ArrayList()
	private var searchResourcesFilter: String = ResourceFilter.DEFAULT_STR
	private var searchResourcesSizeLimit: Int = 0 // 单位 MB

	private var pluginOptions: MutableMap<String, String> = HashMap()

	fun getFiles(): List<Path> = files

	fun setFiles(files: List<Path>) {
		this.files = requireNotNull(files)
	}

	fun getTreeExpansionsV2(): List<String> = treeExpansionsV2

	fun setTreeExpansionsV2(treeExpansionsV2: List<String>) {
		this.treeExpansionsV2 = treeExpansionsV2
	}

	fun getCodeData(): KadxCodeData = codeData

	fun setCodeData(codeData: KadxCodeData) {
		this.codeData = codeData
	}

	fun getProjectVersion(): Int = projectVersion

	fun setProjectVersion(projectVersion: Int) {
		this.projectVersion = projectVersion
	}

	fun getOpenTabs(): List<TabViewState> = openTabs

	/** 设置打开的标签页；内容无变化时返回 `false`，避免触发项目保存。 */
	fun setOpenTabs(openTabs: List<TabViewState>): Boolean {
		if (this.openTabs == openTabs) {
			return false
		}
		this.openTabs = openTabs
		return true
	}

	fun getMappingsPath(): Path? = mappingsPath

	fun setMappingsPath(mappingsPath: Path?) {
		this.mappingsPath = mappingsPath
	}

	fun getCacheDir(): String? = cacheDir

	fun setCacheDir(cacheDir: String?) {
		this.cacheDir = cacheDir
	}

	val isEnableLiveReload: Boolean get() = enableLiveReload

	fun setEnableLiveReload(enableLiveReload: Boolean) {
		this.enableLiveReload = enableLiveReload
	}

	fun getSearchHistory(): MutableList<String> = searchHistory

	fun setSearchHistory(searchHistory: MutableList<String>) {
		this.searchHistory = searchHistory
	}

	fun getSearchResourcesFilter(): String = searchResourcesFilter

	fun setSearchResourcesFilter(searchResourcesFilter: String) {
		this.searchResourcesFilter = searchResourcesFilter
	}

	fun getSearchResourcesSizeLimit(): Int = searchResourcesSizeLimit

	fun setSearchResourcesSizeLimit(searchResourcesSizeLimit: Int) {
		this.searchResourcesSizeLimit = searchResourcesSizeLimit
	}

	fun getPluginOptions(): MutableMap<String, String> = pluginOptions
}
