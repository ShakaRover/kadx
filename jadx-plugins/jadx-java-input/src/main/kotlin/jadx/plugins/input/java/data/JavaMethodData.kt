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
	private val methodRef: JavaMethodRef,
) : IMethodData {

	private var accessFlags = 0

	// 原 Java 字段初始为 null，setData 前调用 getAttributes/getCodeReader 会 NPE；保持等价
	private var attributes: JavaAttrStorage? = null

	fun setData(accessFlags: Int, attributes: JavaAttrStorage) {
		this.accessFlags = accessFlags
		this.attributes = attributes
	}

	// 协变返回：原 Java 覆写返回具体类型 JavaMethodRef，调用方无需转型
	override fun getMethodRef(): JavaMethodRef = methodRef

	override fun getAccessFlags(): Int = accessFlags

	@Nullable
	override fun getCodeReader(): ICodeReader? {
		val codeAttr: CodeAttr? = checkNotNull(this.attributes).get(JavaAttrType.CODE)
		if (codeAttr == null) {
			return null
		}
		return JavaCodeReader(clsData, codeAttr.offset)
	}

	override fun disassembleMethod(): String = ""

	override fun getAttributes(): List<IJadxAttribute> {
		val attributes = checkNotNull(this.attributes)
		val size = attributes.size()
		if (size == 0) {
			return emptyList()
		}
		val list = ArrayList<IJadxAttribute>(size)
		Utils.addToList(list, JavaAnnotationsAttr.merge(attributes))
		Utils.addToList(list, JavaParamAnnsAttr.merge(attributes))
		Utils.addToList(list, JavaAnnotationDefaultAttr.convert(attributes))
		val signature: SignatureAttr? = attributes.get(JavaAttrType.SIGNATURE)
		Utils.addToList(list, signature)
		val exceptions: ExceptionsAttr? = attributes.get(JavaAttrType.EXCEPTIONS)
		Utils.addToList(list, exceptions)
		val methodParameters: JavaMethodParametersAttr? = attributes.get(JavaAttrType.METHOD_PARAMETERS)
		Utils.addToList(list, methodParameters)
		return list
	}

	override fun toString(): String = methodRef.toString()
}
