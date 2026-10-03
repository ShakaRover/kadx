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

	private var nameValue: String? = null
	private var parentClassTypeValue: String? = null
	private var typeValue: String? = null
	private var accessFlagsValue = 0

	// 原 Java 字段初始为 null，setAttributes 前调用 getAttributes 会 NPE；保持等价
	private var attributesValue: JavaAttrStorage? = null

	override val parentClassType: String? get() = parentClassTypeValue

	fun setParentClassType(parentClassTypeValue: String?) {
		this.parentClassTypeValue = parentClassTypeValue
	}

	override val type: String? get() = typeValue

	fun setType(typeValue: String?) {
		this.typeValue = typeValue
	}

	override val name: String? get() = nameValue

	fun setName(nameValue: String?) {
		this.nameValue = nameValue
	}

	override val accessFlags: Int get() = accessFlagsValue

	fun setAccessFlags(accessFlagsValue: Int) {
		this.accessFlagsValue = accessFlagsValue
	}

	fun setAttributes(attributesValue: JavaAttrStorage) {
		this.attributesValue = attributesValue
	}

	override val attributes: List<IJadxAttribute> get() {
		val attributesValue = checkNotNull(this.attributesValue)
		val size = attributesValue.size()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<IJadxAttribute>(size)
		Utils.addToList(list, JavaAnnotationsAttr.merge(attributesValue))
		val constValue: ConstValueAttr? = attributesValue.get(JavaAttrType.CONST_VALUE)
		// 原 Java 用方法引用 ConstValueAttr::getValue；EncodedValue 本身是 IJadxAttribute（PinnedAttribute）
		Utils.addToList(list, constValue) { it.value }
		val signature: SignatureAttr? = attributesValue.get(JavaAttrType.SIGNATURE)
		Utils.addToList(list, signature)
		return list
	}

	override fun toString(): String = parentClassTypeValue + "->" + nameValue + ":" + typeValue
}
