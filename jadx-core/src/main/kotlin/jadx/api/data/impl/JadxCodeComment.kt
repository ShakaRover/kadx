package jadx.api.data.impl

import jadx.api.data.CommentStyle
import jadx.api.data.ICodeComment
import jadx.api.data.IJavaCodeRef
import jadx.api.data.IJavaNodeRef

/**
 * [ICodeComment] 的默认实现，同时是 Gson 反序列化的目标类。
 *
 * **做什么**：保存“挂载节点 + 可选代码引用 + 注释文本 + 注释风格”。
 *
 * **为什么用普通 class（非 data class）**：原 Java 未覆写 `equals/hashCode`，保持身份语义；
 * 另外它需要无参构造器供 Gson 反序列化（`disableJdkUnsafe` 下必须有可调用的无参构造器）。
 *
 * **Kotlin 转换说明**：
 * - 公共 getter 一律显式写成 `getXxx()`，保证 Java 调用方零改动；
 * - 可空字段用 `?`，getter 内用 `checkNotNull` 还原“未初始化即 NPE”的原语义；
 * - 无参构造器由“主构造器所有参数都有默认值”自动生成（Gson 需要）。
 */
class JadxCodeComment(
	nodeRef: IJavaNodeRef? = null,
	codeRef: IJavaCodeRef? = null,
	comment: String? = null,
	style: CommentStyle = CommentStyle.LINE,
) : ICodeComment {

	// 私有属性（不生成 JVM 访问器），Gson 通过反射直接读写这些字段
	private var nodeRef: IJavaNodeRef? = nodeRef
	private var codeRef: IJavaCodeRef? = codeRef
	private var comment: String? = comment
	private var style: CommentStyle = style

	/** 只挂到节点上、使用默认行注释风格。 */
	constructor(nodeRef: IJavaNodeRef, comment: String) : this(nodeRef, null, comment, CommentStyle.LINE)

	/** 只挂到节点上、指定注释风格。 */
	constructor(nodeRef: IJavaNodeRef, comment: String, style: CommentStyle) : this(nodeRef, null, comment, style)

	/** 挂到方法内代码元素上、使用默认行注释风格。 */
	constructor(nodeRef: IJavaNodeRef, codeRef: IJavaCodeRef?, comment: String) : this(nodeRef, codeRef, comment, CommentStyle.LINE)

	override fun getNodeRef(): IJavaNodeRef = checkNotNull(nodeRef) { "nodeRef is not set" }

	fun setNodeRef(nodeRef: IJavaNodeRef) {
		this.nodeRef = nodeRef
	}

	override fun getCodeRef(): IJavaCodeRef? = codeRef

	fun setCodeRef(codeRef: IJavaCodeRef?) {
		this.codeRef = codeRef
	}

	override fun getComment(): String = checkNotNull(comment) { "comment is not set" }

	fun setComment(comment: String) {
		this.comment = comment
	}

	override fun getStyle(): CommentStyle = style

	fun setStyle(style: CommentStyle) {
		this.style = style
	}

	/**
	 * 排序：先按节点引用，再按代码引用（双方都非空时），最后按注释文本。
	 * 与原 Java 逐级比较逻辑完全一致。
	 */
	override fun compareTo(other: ICodeComment): Int {
		val cmpNodeRef = getNodeRef().compareTo(other.getNodeRef())
		if (cmpNodeRef != 0) {
			return cmpNodeRef
		}
		val thisCodeRef = getCodeRef()
		val otherCodeRef = other.getCodeRef()
		if (thisCodeRef != null && otherCodeRef != null) {
			return thisCodeRef.compareTo(otherCodeRef)
		}
		return getComment().compareTo(other.getComment())
	}

	override fun toString(): String = "JadxCodeComment{" + nodeRef +
		", ref=" + codeRef +
		", comment='" + comment + '\'' +
		", style=" + style +
		'}'
}
