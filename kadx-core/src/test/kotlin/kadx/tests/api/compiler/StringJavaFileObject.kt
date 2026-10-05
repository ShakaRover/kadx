package kadx.tests.api.compiler

import java.net.URI
import javax.tools.JavaFileObject.Kind
import javax.tools.SimpleJavaFileObject

/**
 * 由内存字符串提供的 Java 源码文件对象（用于编译反编译输出的代码）。
 */
class StringJavaFileObject(className: String, private val content: String) : SimpleJavaFileObject(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE) {

	override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = content
}
