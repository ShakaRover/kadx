package jadx.core.dex.attributes

import jadx.api.CommentsLevel
import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.core.Consts
import jadx.core.dex.attributes.nodes.JadxCommentsAttr
import jadx.core.utils.Utils

/**
 * [IAttributeNode] 的默认实现基类：把属性读写全部委托给内部的 [AttributeStorage]。
 *
 * **延迟初始化**：初始 [storage] 指向共享的 [EmptyAttrStorage.INSTANCE]；第一次写属性时
 * 才创建真正的 [AttributeStorage]；当属性被清空后又换回空单例。这样大量“无属性”的节点
 * 不会浪费内存。
 *
 * **调试支持**：当 [Consts.DEBUG_ATTRIBUTES] 打开时，每次添加属性都会记录一条 DEBUG 注释，
 * 便于排查属性是在哪个调用栈被加上的。
 *
 * **Kotlin 转换说明**：所有 `override` 方法默认是 open 的，Java 子类（如 InsnNode）可继续覆写；
 * 额外的 `addAttr(type, list)` 重载保留为 open 以便子类扩展。
 */
abstract class AttrNode : IAttributeNode {

	companion object {
		/** 共享的空存储单例，用作初始值 */
		private val EMPTY_ATTR_STORAGE: AttributeStorage = EmptyAttrStorage.INSTANCE
	}

	private var storage: AttributeStorage = EMPTY_ATTR_STORAGE

	override fun add(flag: AFlag) {
		initStorage().add(flag)
		if (Consts.DEBUG_ATTRIBUTES) {
			addDebugComment("Add flag $flag at " + Utils.currentStackTrace(2))
		}
	}

	override fun addAttr(attr: IJadxAttribute) {
		initStorage().add(attr)
		if (Consts.DEBUG_ATTRIBUTES) {
			addDebugComment(
				"Add attribute " + attr.javaClass.simpleName + ": " + attr + " at " + Utils.currentStackTrace(2),
			)
		}
	}

	override fun addAttrs(list: List<IJadxAttribute>) {
		if (list.isEmpty()) {
			return
		}
		initStorage().add(list)
	}

	override fun <T> addAttr(type: IJadxAttrType<AttrList<T>>, obj: T) {
		initStorage().add(type, obj)
		if (Consts.DEBUG_ATTRIBUTES) {
			addDebugComment("Add attribute " + obj + " at " + Utils.currentStackTrace(2))
		}
	}

	/** 重载：一次性向列表型属性追加多个元素（不在 [IAttributeNode] 接口中） */
	open fun <T> addAttr(type: IJadxAttrType<AttrList<T>>, list: List<T>) {
		initStorage().addAttrList(type, list)
	}

	override fun copyAttributesFrom(attrNode: AttrNode) {
		val copyFrom = attrNode.storage
		if (!copyFrom.isEmpty()) {
			initStorage().addAll(copyFrom)
		}
	}

	override fun <T : IJadxAttribute> copyAttributeFrom(attrNode: AttrNode, attrType: AType<T>) {
		val attr = attrNode.get(attrType)
		if (attr != null) {
			this.addAttr(attr)
		}
	}

	/** 移除本节点上的该属性，若来源节点存在则拷贝过来（“覆盖式”复制） */
	override fun <T : IJadxAttribute> rewriteAttributeFrom(attrNode: AttrNode, attrType: AType<T>) {
		remove(attrType)
		copyAttributeFrom(attrNode, attrType)
	}

	private fun initStorage(): AttributeStorage {
		var store = storage
		if (store === EMPTY_ATTR_STORAGE) {
			store = AttributeStorage()
			storage = store
		}
		return store
	}

	private fun unloadIfEmpty() {
		if (storage.isEmpty() && storage !== EMPTY_ATTR_STORAGE) {
			storage = EMPTY_ATTR_STORAGE
		}
	}

	override operator fun contains(flag: AFlag): Boolean = storage.contains(flag)

	override operator fun <T : IJadxAttribute> contains(type: IJadxAttrType<T>): Boolean = storage.contains(type)

	override fun <T : IJadxAttribute> get(type: IJadxAttrType<T>): T? = storage.get(type)

	override fun getAnnotation(cls: String): IAnnotation? = storage.getAnnotation(cls)

	override fun <T> getAll(type: IJadxAttrType<AttrList<T>>): List<T> = storage.getAll(type)

	override fun remove(flag: AFlag) {
		storage.remove(flag)
		unloadIfEmpty()
	}

	override fun <T : IJadxAttribute> remove(type: IJadxAttrType<T>) {
		storage.remove(type)
		unloadIfEmpty()
	}

	override fun removeAttr(attr: IJadxAttribute) {
		storage.remove(attr)
		unloadIfEmpty()
	}

	override fun clearAttributes() {
		storage = EMPTY_ATTR_STORAGE
	}

	open fun unloadAttributes() {
		if (storage === EMPTY_ATTR_STORAGE) {
			return
		}
		storage.unloadAttributes()
		storage.clearFlags()
		unloadIfEmpty()
	}

	override fun getAttributesStringsList(): List<String> = storage.getAttributeStrings()

	override fun getAttributesString(): String = storage.toString()

	override fun isAttrStorageEmpty(): Boolean = storage.isEmpty()

	private fun addDebugComment(msg: String) {
		var commentsAttr = get(AType.JADX_COMMENTS)
		if (commentsAttr == null) {
			commentsAttr = JadxCommentsAttr()
			initStorage().add(commentsAttr)
		}
		commentsAttr.add(CommentsLevel.DEBUG, msg)
	}
}
