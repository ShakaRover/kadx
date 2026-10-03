package jadx.gui.utils

import jadx.gui.JadxWrapper
import jadx.gui.ui.dialog.SearchDialog
import jadx.gui.utils.pkgs.PackageHelper
import java.util.HashMap

/**
 * GUI 的全局缓存对象（挂载在 [jadx.gui.ui.MainWindow] 上）。
 *
 * **做什么**：缓存搜索对话框的上次输入、节点缓存（[JNodeCache]）与包助手（[PackageHelper]）。
 *
 * **注意**：`fullDecompilationFinished` 会被后台线程读写，因此保持 `@Volatile` 语义。
 */
class CacheObject(wrapper: JadxWrapper) {

	private val wrapper: JadxWrapper = wrapper
	private val jNodeCache: JNodeCache = JNodeCache(wrapper)
	private val packageHelper: PackageHelper = PackageHelper(wrapper, jNodeCache)

	private var lastSearch: String? = null
	private var lastSearchOptions: MutableMap<SearchDialog.SearchPreset, MutableSet<SearchDialog.SearchOptions>> = HashMap()
	private var lastSearchPackage: String? = null
	private var maxPkgLength: Int = 0

	@Volatile
	private var fullDecompilationFinished: Boolean = false

	init {
		reset()
	}

	fun reset() {
		lastSearch = null
		jNodeCache.reset()
		lastSearchOptions = HashMap()
		lastSearchPackage = null
		fullDecompilationFinished = false
	}

	fun getLastSearch(): String? = lastSearch

	fun getLastSearchPackage(): String? = lastSearchPackage

	fun setLastSearch(lastSearch: String?) {
		this.lastSearch = lastSearch
	}

	fun setLastSearchPackage(lastSearchPackage: String?) {
		this.lastSearchPackage = lastSearchPackage
	}

	fun getMaxPkgLength(): Int = maxPkgLength

	fun setMaxPkgLength(maxPkgLength: Int) {
		this.maxPkgLength = maxPkgLength
	}

	val nodeCache: JNodeCache get() = jNodeCache

	fun getLastSearchOptions(): MutableMap<SearchDialog.SearchPreset, MutableSet<SearchDialog.SearchOptions>> = lastSearchOptions

	fun getPackageHelper(): PackageHelper = packageHelper

	val isFullDecompilationFinished: Boolean get() = fullDecompilationFinished

	fun setFullDecompilationFinished(fullDecompilationFinished: Boolean) {
		this.fullDecompilationFinished = fullDecompilationFinished
	}
}
