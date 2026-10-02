package jadx.api.data

/**
 * Java 节点引用（类 / 字段 / 方法 / 包）。
 *
 * **做什么**：用 [getType] 区分类别，[getDeclaringClass] 保存声明类全名
 * （包引用时保存包名），[getShortId] 保存字段/方法的短签名（类/包引用可为 null）。
 *
 * **为什么内嵌 `RefType` 枚举**：与原生 Java 的内部枚举保持相同 JVM 名
 * （`jadx.api.data.IJavaNodeRef$RefType`），Java 侧 `IJavaNodeRef.RefType.FIELD` 不变。
 */
interface IJavaNodeRef : Comparable<IJavaNodeRef> {

	/** 节点引用类型。 */
	enum class RefType {
		CLASS,
		FIELD,
		METHOD,
		PKG,
	}

	/** 引用类型。 */
	fun getType(): RefType

	/** 声明类全名（或包名）。 */
	fun getDeclaringClass(): String

	/** 字段/方法短签名；类与包引用可能为 null。 */
	fun getShortId(): String?
}
