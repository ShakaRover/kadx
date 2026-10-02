package jadx.core.utils

/**
 * 文件签名（magic bytes）描述。
 *
 * **用途**：`FileTypeDetector` 用它判断输入文件的真实类型。
 * 签名字符串形如 `"CA FE BA BE"`，其中 `"??"` 表示该字节不参与匹配（通配）。
 */
class FileSignature(fileType: String, signatureHex: String) {

	/** 文件类型描述，如 "class"、"dex" */
	val fileType: String = fileType

	/** 逐字节解析后的签名，`0` 表示通配字节（配合 [matches] 使用） */
	private val signatureBytes: ByteArray

	init {
		val parts = signatureHex.split(" ")
		signatureBytes = ByteArray(parts.size)
		for (i in parts.indices) {
			if (parts[i].length != 2) {
				throw RuntimeException(signatureHex)
			}
			if (parts[i] != "??") {
				signatureBytes[i] = Integer.parseInt(parts[i], 16).toByte()
			}
		}
	}

	companion object {
		/**
		 * 判断数据开头是否匹配签名。
		 * 注意：原实现用 0 表示通配，因此只要签名里有 0 字节就无法区分“通配”和“真 0”，
		 * 这里机械保留同样的行为。
		 */
		@JvmStatic
		fun matches(sig: FileSignature, data: ByteArray): Boolean {
			if (data.size < sig.signatureBytes.size) {
				return false
			}
			for (i in sig.signatureBytes.indices) {
				val b = sig.signatureBytes[i]
				if (b != data[i]) {
					return false
				}
			}
			return true
		}
	}
}
