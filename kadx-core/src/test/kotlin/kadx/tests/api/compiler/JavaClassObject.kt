package kadx.tests.api.compiler

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URI
import javax.tools.JavaFileObject.Kind
import javax.tools.SimpleJavaFileObject

/**
 * 承载编译产物字节码的内存 `JavaFileObject`。
 *
 * 编译器调用 [openOutputStream] 写入 class 字节，测试再通过 [getBytes] 读出落盘。
 */
class JavaClassObject(private val clsName: String, kind: Kind) : SimpleJavaFileObject(URI.create("string:///" + clsName.replace('.', '/') + kind.extension), kind) {

	private val bos = ByteArrayOutputStream()

	override fun getName(): String = clsName

	fun getBytes(): ByteArray = bos.toByteArray()

	override fun openOutputStream(): OutputStream = bos
}
