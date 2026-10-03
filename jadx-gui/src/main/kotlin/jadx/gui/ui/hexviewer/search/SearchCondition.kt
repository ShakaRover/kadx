package jadx.gui.ui.hexviewer.search

import org.exbin.auxiliary.binary_data.BinaryData
import org.exbin.auxiliary.binary_data.EditableBinaryData
import org.exbin.auxiliary.binary_data.array.ByteArrayEditableData
import org.exbin.bined.CodeAreaUtils
import java.util.Objects

/**
 * 搜索条件：既可以按文本搜，也可以按二进制字节搜。
 *
 * **做什么**：保存「搜索模式（文本 / 二进制）、要搜的文本、要搜的二进制数据」。
 * 十六进制视图的搜索框会根据用户输入构造本对象。
 *
 * **注意**：`getBinaryData()` 的返回类型是 [BinaryData]（只读视角），
 * 而 `setBinaryData(...)` 接收 [EditableBinaryData]（可编辑数据），
 * 两者类型不同，因此这里保留显式 getter/setter 函数，不用 Kotlin 属性。
 *
 * 移植自 ExBin Project（Apache-2.0）。
 */
class SearchCondition {

	private var searchMode: SearchMode = SearchMode.TEXT
	private var searchText: String = ""
	private var binaryData: EditableBinaryData? = null

	constructor()

	/**
	 * 拷贝构造：深拷贝源条件中的二进制数据（文本直接复制引用即可）。
	 *
	 * @param source 源搜索条件
	 */
	constructor(source: SearchCondition) {
		searchMode = source.getSearchMode()
		searchText = source.getSearchText()
		binaryData = ByteArrayEditableData()
		val sourceData = source.getBinaryData()
		if (sourceData != null) {
			binaryData?.insert(0L, sourceData)
		}
	}

	fun getSearchMode(): SearchMode = searchMode

	fun setSearchMode(searchMode: SearchMode) {
		this.searchMode = searchMode
	}

	fun getSearchText(): String = searchText

	fun setSearchText(searchText: String) {
		this.searchText = searchText
	}

	fun getBinaryData(): BinaryData? = binaryData

	fun setBinaryData(binaryData: EditableBinaryData?) {
		this.binaryData = binaryData
	}

	/** 当前条件下是否「什么都没输入」，用于跳过空搜索。 */
	val isEmpty: Boolean get() = when (searchMode) {
		SearchMode.TEXT -> searchText.isEmpty()

		SearchMode.BINARY -> binaryData?.isEmpty() ?: true

		// 保留原 Java 的 default 分支语义
		else -> throw CodeAreaUtils.getInvalidTypeException(searchMode)
	}

	override fun hashCode(): Int = 3

	override fun equals(obj: Any?): Boolean {
		if (this === obj) {
			return true
		}
		if (obj == null) {
			return false
		}
		if (javaClass != obj.javaClass) {
			return false
		}
		val other = obj as SearchCondition
		if (searchMode != other.searchMode) {
			return false
		}
		return if (searchMode == SearchMode.TEXT) {
			Objects.equals(searchText, other.searchText)
		} else {
			Objects.equals(binaryData, other.binaryData)
		}
	}

	/** 清空搜索内容（保留搜索模式）。 */
	fun clear() {
		searchText = ""
		binaryData?.clear()
	}

	enum class SearchMode {
		TEXT,
		BINARY,
	}
}
