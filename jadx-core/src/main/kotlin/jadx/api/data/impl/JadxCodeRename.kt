package jadx.api.data.impl

import jadx.api.data.ICodeRename
import jadx.api.data.IJavaCodeRef
import jadx.api.data.IJavaNodeRef

/**
 * [ICodeRename] 的默认实现，同时是工程文件 JSON 的读写载体。
 *
 * **做什么**：保存“目标节点 + 可选代码引用 + 新名字”。
 *
 * **为什么用普通 class**：有自定义 `equals/hashCode`（按节点 + 代码引用判等，
 * 不含新名字），且需要无参构造器供 Gson 使用。
 *
 * **注意**：`equals` 判断的是 `ICodeRename` 接口（不是本类），与原 Java 完全一致。
 */
class JadxCodeRename(
	nodeRef: IJavaNodeRef? = null,
	codeRef: IJavaCodeRef? = null,
	newName: String? = null,
) : ICodeRename {

	// 私有属性，Gson 反射读写
	private var nodeRef: IJavaNodeRef? = nodeRef
	private var codeRef: IJavaCodeRef? = codeRef
	private var newName: String? = newName

	/** 重命名节点本身（不带方法内代码引用）。 */
	constructor(nodeRef: IJavaNodeRef, newName: String) : this(nodeRef, null, newName)

	override fun getNodeRef(): IJavaNodeRef = checkNotNull(nodeRef) { "nodeRef is not set" }

	fun setNodeRef(nodeRef: IJavaNodeRef) {
		this.nodeRef = nodeRef
	}

	override fun getCodeRef(): IJavaCodeRef? = codeRef

	fun setCodeRef(codeRef: IJavaCodeRef?) {
		this.codeRef = codeRef
	}

	override fun getNewName(): String = checkNotNull(newName) { "newName is not set" }

	fun setNewName(newName: String) {
		this.newName = newName
	}

	/** 排序：先按节点引用，再按代码引用（双方都非空时），最后按新名字。 */
	override fun compareTo(other: ICodeRename): Int {
		val cmpNodeRef = getNodeRef().compareTo(other.getNodeRef())
		if (cmpNodeRef != 0) {
			return cmpNodeRef
		}
		val thisCodeRef = getCodeRef()
		val otherCodeRef = other.getCodeRef()
		if (thisCodeRef != null && otherCodeRef != null) {
			return thisCodeRef.compareTo(otherCodeRef)
		}
		return getNewName().compareTo(other.getNewName())
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other !is ICodeRename) {
			return false
		}
		return getNodeRef() == other.getNodeRef() && getCodeRef() == other.getCodeRef()
	}

	override fun hashCode(): Int = 31 * getNodeRef().hashCode() + (getCodeRef()?.hashCode() ?: 0)

	override fun toString(): String = "JadxCodeRename{" + nodeRef +
		", codeRef=" + codeRef +
		", newName='" + newName + '\'' +
		'}'
}
