package jadx.core.xmlgen

import java.io.IOException
import java.io.InputStream

/**
 * 资源表解析器接口。
 *
 * 具体实现分别处理二进制 `.arsc`（[ResTableBinaryParser]）与 AAB 的 protobuf 资源表。
 * 接口保持 Java 可实现：`getResStorage` / `getStrings` 在解析完成前可能为 null，
 * 故声明为可空；`setBaseFileName` 提供默认空实现，Java 实现方可选覆写。
 */
interface IResTableParser {

	/** 解析资源表数据（读取原始字节流）。 */
	@Throws(IOException::class)
	fun decode(inputStream: InputStream)

	/** 生成可保存的资源文件树。 */
	fun decodeFiles(): ResContainer

	/** 解析结果存储；在 [decode] 之前为 null。 */
	fun getResStorage(): ResourceStorage?

	/** 字符串池；在 [decode] 之前为 null。 */
	fun getStrings(): BinaryXMLStrings?

	/** 可选：设置输出文件名。默认空实现。 */
	fun setBaseFileName(fileName: String) {
		// optional
	}
}
