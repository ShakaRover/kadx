package jadx.core.xmlgen

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * 二进制解析器公共基类：提供字符串池（string pool）的通用解析与统一报错。
 *
 * 子类（[BinaryXMLParser] / [ResTableBinaryParser]）在解析前把 [input] 指向实际字节流。
 * 字符串池格式见 AOSP `ResStringPool_header`。
 */
open class CommonBinaryParser : ParserConstants() {

	protected lateinit var input: ParserStream

	/** 解析带类型头的字符串池（chunk 头之后立即调用）。 */
	@Throws(IOException::class)
	protected fun parseStringPool(): BinaryXMLStrings {
		input.checkInt16(ParserConstants.RES_STRING_POOL_TYPE, "String pool expected")
		return parseStringPoolNoType()
	}

	/** 解析不带类型头的字符串池（类型已在别处读取）。 */
	@Throws(IOException::class)
	protected fun parseStringPoolNoType(): BinaryXMLStrings {
		val start = input.pos - 2
		val headerSize = input.readInt16()
		if (headerSize != 0x1c) {
			LOG.warn("Unexpected string pool header size: 0x{}, expected: 0x1C", Integer.toHexString(headerSize))
		}
		val size = input.readUInt32()
		val chunkEnd = start + size

		return parseStringPoolNoSize(start, chunkEnd)
	}

	/** 解析字符串池主体（头部已读取，[start] 为 chunk 起点，[chunkEnd] 为 chunk 结束偏移）。 */
	@Throws(IOException::class)
	protected fun parseStringPoolNoSize(start: Long, chunkEnd: Long): BinaryXMLStrings {
		val stringCount = input.readInt32()
		val styleCount = input.readInt32()
		val flags = input.readInt32()
		var stringsStart = input.readInt32().toLong()
		input.readInt32() // stylesStart（保留读取以推进位置）

		// Correct the offset of actual strings, as the header is already read.
		stringsStart = stringsStart - (input.pos - start)
		val buffer = input.readInt8Array((chunkEnd - input.pos).toInt())
		input.checkPos(chunkEnd, "Expected strings pool end")

		return BinaryXMLStrings(
			stringCount,
			stringsStart,
			buffer,
			(flags and ParserConstants.UTF8_FLAG) != 0,
		)
	}

	/** 抛出带当前偏移量的解码错误。 */
	@Throws(IOException::class)
	protected fun die(message: String): Unit = throw IOException(
		"Decode error: $message, position: 0x" + java.lang.Long.toHexString(input.pos),
	)

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(CommonBinaryParser::class.java)
	}
}
