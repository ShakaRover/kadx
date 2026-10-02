package jadx.core.dex.attributes

import jadx.api.plugins.input.data.annotations.IAnnotation
import jadx.api.plugins.input.data.attributes.IJadxAttrType
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import java.util.Collections

/**
 * 空属性存储单例：所有“读”操作都返回空/null，所有“写”操作都被忽略。
 *
 * **用途**：绝大多数节点在反编译早期没有任何属性，[AttrNode] 用本单例作为初始值，
 * 避免为每个节点都 new 一个 [AttributeStorage]。真正需要写属性时再换成普通实例。
 *
 * **Kotlin 转换说明**：Java 的 `public static final INSTANCE` 用 companion +
 * [JvmField] 平替，Java 调用方仍写 `EmptyAttrStorage.INSTANCE`。
 */
class EmptyAttrStorage private constructor() : AttributeStorage() {

	companion object {
		/** 全局共享的单例实例（类型故意声明为父类 [AttributeStorage]，与原 Java 一致） */
		@JvmField
		val INSTANCE: AttributeStorage = EmptyAttrStorage()
	}

	override operator fun contains(flag: AFlag): Boolean = false

	override operator fun <T : IJadxAttribute> contains(type: IJadxAttrType<T>): Boolean = false

	override fun <T : IJadxAttribute> get(type: IJadxAttrType<T>): T? = null

	override fun getAnnotation(cls: String): IAnnotation? = null

	override fun <T> getAll(type: IJadxAttrType<AttrList<T>>): List<T> = Collections.emptyList()

	override fun remove(flag: AFlag) {
		// 空存储：忽略
	}

	override fun <T : IJadxAttribute> remove(type: IJadxAttrType<T>) {
		// 空存储：忽略
	}

	override fun remove(attr: IJadxAttribute) {
		// 空存储：忽略
	}

	override fun getAttributeStrings(): List<String> = Collections.emptyList()

	override fun isEmpty(): Boolean = true

	override fun toString(): String = ""
}
