package jadx.gui.utils

/**
 * 十六进制字符串工具。
 *
 * **做什么**：校验十六进制字符串、把十六进制字符串转换为字节数组。
 *
 * **为什么用 `object`**：原 Java 类全部是静态方法，`object` + `@JvmStatic`
 * 保持 Java 侧 `HexUtils.xxx(...)` 调用方式不变。
 */
object HexUtils {

	/**
	 * 判断字符串是否为合法的十六进制串（忽略空格）。
	 *
	 * 规则：去掉空格后长度必须为偶数，且能被 [java.lang.Long.parseLong] 按 16 进制解析。
	 */
	@JvmStatic
	fun isValidHexString(hexString: String): Boolean {
		val cleanS = hexString.replace(" ", "")
		val len = cleanS.length
		return try {
			val isPair = len % 2 == 0
			if (isPair) {
				java.lang.Long.parseLong(cleanS, 16)
				true
			} else {
				false
			}
		} catch (ex: NumberFormatException) {
			// 解析失败说明含有非十六进制字符
			false
		}
	}

	/**
	 * 把十六进制字符串转换为字节数组（忽略空格）。
	 *
	 * @throws IllegalArgumentException 长度为奇数或含有非十六进制字符时抛出
	 */
	@JvmStatic
	fun hexStringToByteArray(hexString: String?): ByteArray {
		if (hexString == null || hexString.isEmpty()) {
			return ByteArray(0)
		}
		val cleanS = hexString.replace(" ", "")
		val len = cleanS.length
		if (!isValidHexString(hexString)) {
			throw IllegalArgumentException("Hex string must have even length. Input length: $len")
		}

		val data = ByteArray(len / 2)
		for (i in 0 until len step 2) {
			val byteString = cleanS.substring(i, i + 2)
			try {
				val intValue = Integer.parseInt(byteString, 16)
				data[i / 2] = intValue.toByte()
			} catch (e: NumberFormatException) {
				throw IllegalArgumentException("Input string contains non-hex characters at index $i: $byteString", e)
			}
		}
		return data
	}
}
