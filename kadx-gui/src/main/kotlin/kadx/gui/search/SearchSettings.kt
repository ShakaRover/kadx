package kadx.gui.search

import kadx.api.JavaClass
import kadx.api.JavaPackage
import kadx.api.KadxDecompiler
import kadx.core.utils.exceptions.InvalidDataException
import kadx.gui.search.providers.ResourceFilter
import kadx.gui.treemodel.JClass
import kadx.gui.treemodel.JResource
import kadx.gui.ui.MainWindow
import kadx.gui.utils.NLS
import java.util.regex.Pattern

/**
 * 一次搜索的全部设置。
 *
 * **做什么**：保存搜索词、是否正则 / 忽略大小写、限定包名、资源过滤器与大小上限，
 * 以及当前激活的类 / 资源。调用 [prepare] 后还会编译出正则、匹配方法与资源过滤器。
 *
 * **为什么保留显式 getter/setter**：`SearchDialog`、各搜索提供者都按原 Java 方法名
 * 访问（`isUseRegex()` / `setSearchPkgStr()` 等），显式函数可以零改动兼容；
 * 同时避免 Kotlin 布尔属性把 `isXxx()` 生成成 `getXxx()` 的命名陷阱。
 *
 * **为什么不是 `data class`**：它是可变的会话对象，需要按身份比较。
 */
class SearchSettings(private val searchString: String) {

	private var useRegex = false
	private var ignoreCase = false
	private var searchPkgStr: String = ""
	private var resFilterStr: String = ""
	private var resSizeLimit = 0 // 单位 MB

	private var activeCls: JClass? = null
	private var activeResource: JResource? = null
	private var regexPattern: Pattern? = null
	private var searchMethod: ISearchMethod? = null
	private var searchPackage: JavaPackage? = null
	private var resourceFilter: ResourceFilter? = null

	/**
	 * 在真正开始搜索前做准备工作。
	 *
	 * @return 出错时返回给用户看的错误文案；一切正常返回 `null`
	 */
	fun prepare(mainWindow: MainWindow): String? {
		if (useRegex) {
			try {
				val flags = if (ignoreCase) Pattern.CASE_INSENSITIVE else 0
				this.regexPattern = Pattern.compile(searchString, flags)
			} catch (e: Exception) {
				return "Invalid Regex: " + e.message
			}
		}
		if (!searchPkgStr.isBlank()) {
			val decompiler: KadxDecompiler = mainWindow.getWrapper().getDecompiler()
			val pkg = checkNotNull(decompiler.getRoot()).resolvePackage(searchPkgStr)
			if (pkg == null) {
				return NLS.str("search_dialog.package_not_found")
			}
			searchPackage = pkg.javaNode
		}
		searchMethod = ISearchMethod.build(this)
		try {
			resourceFilter = ResourceFilter.parse(resFilterStr)
		} catch (e: InvalidDataException) {
			return "Invalid resource file filter: " + e.message
		}
		return null
	}

	/** 判断一段文本是否命中当前搜索条件。 */
	fun isMatch(searchArea: String): Boolean = getSearchMethod().find(searchArea, searchString, 0) != -1

	val isUseRegex: Boolean get() = useRegex

	fun setUseRegex(useRegex: Boolean) {
		this.useRegex = useRegex
	}

	val isIgnoreCase: Boolean get() = ignoreCase

	fun setIgnoreCase(ignoreCase: Boolean) {
		this.ignoreCase = ignoreCase
	}

	fun getSearchPackage(): JavaPackage? = searchPackage

	/** 判断某个类是否位于限定的搜索包之下。 */
	fun isInSearchPkg(cls: JavaClass): Boolean {
		val pkg = cls.getJavaPackage() ?: return false
		return pkg.isDescendantOf(checkNotNull(searchPackage))
	}

	fun setSearchPkgStr(searchPkgStr: String) {
		this.searchPkgStr = searchPkgStr
	}

	fun getSearchString(): String = searchString

	/** 正则模式；[prepare] 在 `useRegex` 为 true 时保证已编译。 */
	val pattern: Pattern get() = checkNotNull(regexPattern)

	fun getActiveCls(): JClass? = activeCls

	fun setActiveCls(activeCls: JClass?) {
		this.activeCls = activeCls
	}

	fun getActiveResource(): JResource? = activeResource

	fun setActiveResource(activeResource: JResource?) {
		this.activeResource = activeResource
	}

	/** 匹配方法；[prepare] 会保证其已构建。 */
	fun getSearchMethod(): ISearchMethod = checkNotNull(searchMethod)

	fun setResFilterStr(resFilterStr: String) {
		this.resFilterStr = resFilterStr
	}

	/** 资源过滤器；[prepare] 会保证其已解析。 */
	fun getResourceFilter(): ResourceFilter = checkNotNull(resourceFilter)

	fun getResSizeLimit(): Int = resSizeLimit

	fun setResSizeLimit(resSizeLimit: Int) {
		this.resSizeLimit = resSizeLimit
	}
}
