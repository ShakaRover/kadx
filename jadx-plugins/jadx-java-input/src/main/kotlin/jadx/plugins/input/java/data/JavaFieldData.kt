package jadx.plugins.input.java.data

import jadx.api.plugins.input.data.IFieldData
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.api.plugins.input.data.attributes.types.SignatureAttr
import jadx.api.plugins.utils.Utils
import jadx.plugins.input.java.data.attributes.JavaAttrStorage
import jadx.plugins.input.java.data.attributes.JavaAttrType
import jadx.plugins.input.java.data.attributes.types.ConstValueAttr
import jadx.plugins.input.java.data.attributes.types.JavaAnnotationsAttr
import java.util.ArrayList

/**
 * class 文件中的单个字段数据。
 *
 **做什么**：保存名字/所属类/类型描述符/access flags 与属性存储；
 * [getAttributes] 把注解、常量值、签名等属性摊平成通用 [IJadxAttribute] 列表交给 jadx-core。
 */
class JavaFieldData : IFieldData {

	private var name: String? = null
	private var parentClassType: String? = null
	private var type: String? = null
	private var accessFlags = 0

	// 原 Java 字段初始为 null，setAttributes 前调用 getAttributes 会 NPE；保持等价
	private var attributes: JavaAttrStorage? = null

	override fun getParentClassType(): String? = parentClassType

	fun setParentClassType(parentClassType: String?) {
		this.parentClassType = parentClassType
	}

	override fun getType(): String? = type

	fun setType(type: String?) {
		this.type = type
	}

	override fun getName(): String? = name

	fun setName(name: String?) {
		this.name = name
	}

	override fun getAccessFlags(): Int = accessFlags

	fun setAccessFlags(accessFlags: Int) {
		this.accessFlags = accessFlags
	}

	fun setAttributes(attributes: JavaAttrStorage) {
		this.attributes = attributes
	}

	override fun getAttributes(): List<IJadxAttribute> {
		val attributes = checkNotNull(this.attributes)
		val size = attributes.size()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<IJadxAttribute>(size)
		Utils.addToList(list, JavaAnnotationsAttr.merge(attributes))
		val constValue: ConstValueAttr? = attributes.get(JavaAttrType.CONST_VALUE)
		// 原 Java 用方法引用 ConstValueAttr::getValue；EncodedValue 本身是 IJadxAttribute（PinnedAttribute）
		Utils.addToList(list, constValue) { it.value }
		val signature: SignatureAttr? = attributes.get(JavaAttrType.SIGNATURE)
		Utils.addToList(list, signature)
		return list
	}

	override fun toString(): String = parentClassType + "->" + name + ":" + type
}
