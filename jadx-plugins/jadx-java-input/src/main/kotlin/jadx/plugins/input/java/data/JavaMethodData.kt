package jadx.plugins.input.java.data

import jadx.api.plugins.input.data.ICodeReader
import jadx.api.plugins.input.data.IMethodData
import jadx.api.plugins.input.data.attributes.IJadxAttribute
import jadx.api.plugins.input.data.attributes.types.ExceptionsAttr
import jadx.api.plugins.input.data.attributes.types.SignatureAttr
import jadx.api.plugins.utils.Utils
import jadx.plugins.input.java.data.attributes.JavaAttrStorage
import jadx.plugins.input.java.data.attributes.JavaAttrType
import jadx.plugins.input.java.data.attributes.types.CodeAttr
import jadx.plugins.input.java.data.attributes.types.JavaAnnotationDefaultAttr
import jadx.plugins.input.java.data.attributes.types.JavaAnnotationsAttr
import jadx.plugins.input.java.data.attributes.types.JavaMethodParametersAttr
import jadx.plugins.input.java.data.attributes.types.JavaParamAnnsAttr
import jadx.plugins.input.java.data.code.JavaCodeReader
import org.jetbrains.annotations.Nullable
import java.util.ArrayList

/**
 * class 文件中的单个方法数据。
 *
 **做什么**：持有 [JavaMethodRef]（名字/描述符）+ access flags + 属性存储；
 * [getCodeReader] 按需创建 [JavaCodeReader] 解析字节码；[getAttributes] 摊平注解类属性。
 */
class JavaMethodData(
	private val clsData: JavaClassData,
	private val methodRefValue: JavaMethodRef,
) : IMethodData {

	private var accessFlagsValue = 0

	// 原 Java 字段初始为 null，setData 前调用 getAttributes/getCodeReader 会 NPE；保持等价
	private var attributesValue: JavaAttrStorage? = null

	fun setData(accessFlagsValue: Int, attributesValue: JavaAttrStorage) {
		this.accessFlagsValue = accessFlagsValue
		this.attributesValue = attributesValue
	}

	// 协变返回：原 Java 覆写返回具体类型 JavaMethodRef，调用方无需转型
	override val methodRef: JavaMethodRef get() = methodRefValue

	override val accessFlags: Int get() = accessFlagsValue

	@get:Nullable
	override val codeReader: ICodeReader? get() {
		val codeAttr: CodeAttr? = checkNotNull(this.attributesValue).get(JavaAttrType.CODE)
		if (codeAttr == null) {
			return null
		}
		return JavaCodeReader(clsData, codeAttr.offset)
	}

	override fun disassembleMethod(): String = ""

	override val attributes: List<IJadxAttribute> get() {
		val attributesValue = checkNotNull(this.attributesValue)
		val size = attributesValue.size()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<IJadxAttribute>(size)
		Utils.addToList(list, JavaAnnotationsAttr.merge(attributesValue))
		Utils.addToList(list, JavaParamAnnsAttr.merge(attributesValue))
		Utils.addToList(list, JavaAnnotationDefaultAttr.convert(attributesValue))
		val signature: SignatureAttr? = attributesValue.get(JavaAttrType.SIGNATURE)
		Utils.addToList(list, signature)
		val exceptions: ExceptionsAttr? = attributesValue.get(JavaAttrType.EXCEPTIONS)
		Utils.addToList(list, exceptions)
		val methodParameters: JavaMethodParametersAttr? = attributesValue.get(JavaAttrType.METHOD_PARAMETERS)
		Utils.addToList(list, methodParameters)
		return list
	}

	override fun toString(): String = methodRefValue.toString()
}
