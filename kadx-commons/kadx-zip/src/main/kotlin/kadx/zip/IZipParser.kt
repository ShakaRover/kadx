package kadx.zip

import java.io.Closeable
import java.io.IOException

/**
 * Zip 解析器接口。
 *
 * 实现类负责读取 zip 文件并返回其内容 [ZipContent]；
 * 解析完成后应调用 close()（继承自 [Closeable]）释放资源。
 */
interface IZipParser : Closeable {
	// @Throws 让 Java 编译器把 IOException 当受检异常处理，保持与原接口签名一致
	@Throws(IOException::class)
	fun open(): ZipContent
}
