package jadx.api.data.impl

import jadx.api.data.ICodeComment
import jadx.api.data.ICodeData
import jadx.api.data.ICodeRename

/**
 * [ICodeData] 的默认实现，同时是工程文件 JSON 的读写载体。
 *
 * **做什么**：持有注释与重命名两个列表，默认都是空列表（原 Java 用 `Collections.emptyList()`）。
 *
 * **为什么用普通 class**：需要无参构造器供 Gson 反序列化；属性初始化为只读空列表，
 * 调用方通过 setter 换成可变列表（测试里就是这么做的）。
 */
class JadxCodeData : ICodeData {

	// 私有属性，Gson 反射读写；getter 显式实现接口方法
	private var comments: List<ICodeComment> = emptyList()
	private var renames: List<ICodeRename> = emptyList()

	override fun getComments(): List<ICodeComment> = comments

	fun setComments(comments: List<ICodeComment>) {
		this.comments = comments
	}

	override fun getRenames(): List<ICodeRename> = renames

	fun setRenames(renames: List<ICodeRename>) {
		this.renames = renames
	}

	override fun isEmpty(): Boolean = comments.isEmpty() && renames.isEmpty()
}
