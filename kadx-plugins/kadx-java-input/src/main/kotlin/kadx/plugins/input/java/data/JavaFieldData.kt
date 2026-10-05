package kadx.plugins.input.java.data

import kadx.api.plugins.input.data.IFieldData
import kadx.api.plugins.input.data.attributes.IKadxAttribute
import kadx.api.plugins.input.data.attributes.types.SignatureAttr
import kadx.api.plugins.utils.Utils
import kadx.plugins.input.java.data.attributes.JavaAttrStorage
import kadx.plugins.input.java.data.attributes.JavaAttrType
import kadx.plugins.input.java.data.attributes.types.ConstValueAttr
import kadx.plugins.input.java.data.attributes.types.JavaAnnotationsAttr
import java.util.ArrayList

/**
 * class 文件中的单个字段数据。
 *
 **做什么**：保存名字/所属类/类型描述符/access flags 与属性存储；
 * [getAttributes] 把注解、常量值、签名等属性摊平成通用 [IKadxAttribute] 列表交给 kadx-core。
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

	override val attributes: List<IKadxAttribute> get() {
		val attributesValue = checkNotNull(this.attributesValue)
		val size = attributesValue.size()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<IKadxAttribute>(size)
		Utils.addToList(list, JavaAnnotationsAttr.merge(attributesValue))
		val constValue: ConstValueAttr? = attributesValue.get(JavaAttrType.CONST_VALUE)
		// 原 Java 用方法引用 ConstValueAttr::getValue；EncodedValue 本身是 IKadxAttribute（PinnedAttribute）
		Utils.addToList(list, constValue) { it.value }
		val signature: SignatureAttr? = attributesValue.get(JavaAttrType.SIGNATURE)
		Utils.addToList(list, signature)
		return list
	}

	override fun toString(): String = parentClassTypeValue + "->" + nameValue + ":" + typeValue
}
