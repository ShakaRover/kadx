package kadx.core.dex.attributes

import kadx.api.plugins.input.data.annotations.IAnnotation
import kadx.api.plugins.input.data.attributes.IKadxAttrType
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import java.util.Collections

/**
 * 空属性存储单例：所有“读”操作都返回空/null，所有“写”操作都被忽略。
 *
 * **用途**：绝大多数节点在反编译早期没有任何属性，[AttrNode] 用本单例作为初始值，
 * 避免为每个节点都 new 一个 [AttributeStorage]。真正需要写属性时再换成普通实例。
 *
 * **Kotlin 转换说明**：单例放入 `companion object`，Kotlin 侧写 `EmptyAttrStorage.INSTANCE`。
 */
class EmptyAttrStorage private constructor() : AttributeStorage() {

	companion object {
		/** 全局共享的单例实例（类型故意声明为父类 [AttributeStorage]，与原 Java 一致） */
		val INSTANCE: AttributeStorage = EmptyAttrStorage()
	}

	override operator fun contains(flag: AFlag): Boolean = false

	override operator fun <T : IKadxAttribute> contains(type: IKadxAttrType<T>): Boolean = false

	override fun <T : IKadxAttribute> get(type: IKadxAttrType<T>): T? = null

	override fun getAnnotation(cls: String): IAnnotation? = null

	override fun <T> getAll(type: IKadxAttrType<AttrList<T>>): List<T> = Collections.emptyList()

	override fun remove(flag: AFlag) {
		// 空存储：忽略
	}

	override fun <T : IKadxAttribute> remove(type: IKadxAttrType<T>) {
		// 空存储：忽略
	}

	override fun remove(attr: IKadxAttribute) {
		// 空存储：忽略
	}

	override fun getAttributeStrings(): List<String> = Collections.emptyList()

	override fun isEmpty(): Boolean = true

	override fun toString(): String = ""
}
