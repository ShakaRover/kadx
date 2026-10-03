package jadx.plugins.input.java.utils

import java.nio.charset.StandardCharsets

/**
 * Modified UTF-8 解码器（JVM 常量池字符串专用编码，见 JVMS §4.4.7）。
 *
 * **做什么**：把 .class 文件常量池中的 UTF8 条目字节序列还原为 Java String。
 * 与标准 UTF-8 的差异：NUL 字符用两字节 `0xC0 0x80` 编码（而非单字节 0），
 * 且支持 6 字节的代理对形式表示增补平面字符（U+10000 以上）。
 *
 * **为什么单独实现**：标准 UTF-8 解码器无法正确处理 NUL 的 2 字节编码，
 * 直接复用会丢失字符串中的 '\0'。
 */
object ModifiedUTF8Decoder {

	/**
	 * 解码整个字节数组为字符串。
	 *
	 * @throws JavaClassParseException 字节序列结构不一致（截断、非法多字节序列）时抛出
	 */
	fun decodeString(bytes: ByteArray): String {
		val len = bytes.size

		// 快速路径：若所有字节都是 7-bit ASCII，直接用 US_ASCII 解码
		var asciiStr = true
		for (b in bytes) {
			if ((b.toInt() and 0x80) != 0) {
				asciiStr = false
				break
			}
		}
		if (asciiStr) {
			return String(bytes, StandardCharsets.US_ASCII)
		}

		// 按 jvms-4.4.7 逐字节解析 modified UTF-8
		val sb = StringBuilder()
		var i = 0
		while (i < len) {
			var x = bytes[i].toInt() and 0xff
			if ((x and 0x80) == 0) {
				// 1 字节 7-bit ASCII（Table 4.4./4.5）
				sb.append(x.toChar())
			} else {
				if (i + 1 >= len) {
					throw JavaClassParseException("Inconsistent byte array structure: too short")
				}
				var y = bytes[i + 1].toInt() and 0xff
				// NUL 编码为 0xC0 0x80（jvms-4.4.7）
				if (x == 0xc0 && y == 0x80) {
					sb.appendCodePoint(0)
					i++
				} else if ((x and 0xE0) == 0xC0 && (y and 0xC0) == 0x80) {
					// 2 字节字符（Table 4.8./4.9）
					sb.appendCodePoint(((x and 0x1f) shl 6) + (y and 0x3f))
					i++
				} else if (i + 2 < len) {
					var z = bytes[i + 2].toInt() and 0xff
					if ((x and 0xF0) == 0xE0 && (y and 0xC0) == 0x80 && (z and 0xC0) == 0x80) {
						// 3 字节字符（Table 4.11/4.12）
						sb.appendCodePoint(((x and 0xf) shl 12) + ((y and 0x3f) shl 6) + (z and 0x3f))
						i += 2
					} else if (i + 5 < len &&
						x == 0xED && // u
						(y and 0xF0) == 0xA0 && // v
						(bytes[i + 3].toInt() and 0xff) == 0xED && // x
						(bytes[i + 4].toInt() and 0xF0) == 0xA0 // y
					) {
						// 6 字节编码的代理对（Table 4.12），表示 U+10000 以上字符
						val u = x // 0
						val v = y // 1
						val w = z // 2
						x = bytes[i + 3].toInt() and 0xff
						y = bytes[i + 4].toInt() and 0xff
						z = bytes[i + 5].toInt() and 0xff
						if (x == 0xED && (y and 0xF0) == 0xA0) {
							sb.appendCodePoint(0x10000 + ((v and 0x0f) shl 16) + ((w and 0x3f) shl 10) + ((y and 0x0f) shl 6) + (z and 0x3f))
							i += 5
						} else {
							throw JavaClassParseException("Inconsistent byte array structure: invalid 6 bytes char")
						}
					} else {
						throw JavaClassParseException("Inconsistent byte array structure: unexpected char")
					}
				}
			}
			i++
		}
		return sb.toString()
	}
}
