package kadx.plugins.input.java.data.attributes.types

import kadx.api.plugins.input.data.attributes.types.SignatureAttr
import kadx.plugins.input.java.data.DataReader
import kadx.plugins.input.java.data.JavaClassData
import kadx.plugins.input.java.data.attributes.IJavaAttribute
import kadx.plugins.input.java.data.attributes.IJavaAttributeReader

/**
 * Signature attribute：泛型签名的完整描述符形式。
 *
 **做什么**：读取签名字符串（如 `(Ljava/lang/String;)Ljava/util/List<java.lang.String>;`），
 * 供后续还原泛型类型时使用；继承通用 [SignatureAttr] 并挂上 IJavaAttribute 标记。
 */
class JavaSignatureAttr(signature: String) :
	SignatureAttr(signature),
	IJavaAttribute {

	companion object {
		/** @return 读取器：读一个 u2 索引取签名字符串 */
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute = JavaSignatureAttr(checkNotNull(clsData.constPoolReader.getUtf8(reader.readU2())))
		}
	}
}
