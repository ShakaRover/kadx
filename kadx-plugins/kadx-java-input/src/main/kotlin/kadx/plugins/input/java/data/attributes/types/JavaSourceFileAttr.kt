package kadx.plugins.input.java.data.attributes.types

import kadx.api.plugins.input.data.attributes.types.SourceFileAttr
import kadx.plugins.input.java.data.DataReader
import kadx.plugins.input.java.data.JavaClassData
import kadx.plugins.input.java.data.attributes.IJavaAttribute
import kadx.plugins.input.java.data.attributes.IJavaAttributeReader

/**
 * SourceFile attribute：编译该 class 的源文件名。
 *
 **做什么**：读取文件名（如 "Foo.java"），用于输出文件命名与调试信息；
 * 继承通用 [SourceFileAttr] 并挂上 IJavaAttribute 标记。
 */
class JavaSourceFileAttr(fileName: String) :
	SourceFileAttr(fileName),
	IJavaAttribute {

	companion object {
		/** @return 读取器：读一个 u2 索引取文件名 */
		fun reader(): IJavaAttributeReader = object : IJavaAttributeReader {
			override fun read(clsData: JavaClassData, reader: DataReader): IJavaAttribute = JavaSourceFileAttr(checkNotNull(clsData.constPoolReader.getUtf8(reader.readU2())))
		}
	}
}
