package jadx.core.deobf

import jadx.core.utils.FileSignature
import jadx.core.utils.StringUtils
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 文件类型探测器：根据文件头（magic bytes）或文本内容推断文件扩展名。
 *
 * **用途**：Android APK 里的 `resources.arsc` / `assets` 中的文件往往没有扩展名，
 * 导出资源时需要给它们补一个合理的后缀（如 `.png`、`.xml`、`.mp4`）。
 *
 * **检测顺序**：
 * 1. 先按二进制文件头匹配（[FILE_SIGNATURES]，含通配字节 `??`）；
 * 2. 再按文本内容判断（证书、私钥、HTML、DOCTYPE 声明的文档类型）；
 * 3. 最后尝试当作 XML 解析，按根标签推断（`svg` / `plist` / `kml` / 普通 `xml`）。
 */
class FileTypeDetector {

	companion object {
		/** DOCTYPE 声明解析：`<!doctype html>` 之类，捕获文档类型名 */
		private val DOCTYPE_PATTERN: Pattern = Pattern.compile("\\s*<!doctype *(\\w+)[ >]", Pattern.CASE_INSENSITIVE)

		/** 已注册的文件头签名列表 */
		private val FILE_SIGNATURES = ArrayList<FileSignature>()

		init {
			register("png", "89 50 4E 47")
			register("jpg", "FF D8 FF")
			register("gif", "47 49 46 38")
			register("webp", "52 49 46 46 ?? ?? ?? ?? 57 45 42 50 56 50 38")
			register("bmp", "42 4D")
			register("bmp", "42 41")
			register("bmp", "43 49")
			register("bmp", "43 50")
			register("bmp", "49 43")
			register("bmp", "50 54")
			register("mp4", "00 00 00 ?? 66 74 79 70 69 73 6F 36")
			register("mp4", "00 00 00 ?? 66 74 79 70 6D 70 34 32")
			register("m4a", "00 00 00 ?? 66 74 79 70 4D 34 41 20")
			register("mp3", "49 44 33")
			register("ogg", "4F 67 67 53")
			register("wav", "52 49 46 46 ?? ?? ?? ?? 57 41 56 45")
			register("ttf", "00 01 00 00")
			register("ttc", "74 74 63 66")
			register("otf", "4F 54 54 4F")
			register("xml", "03 00 08 00")
		}

		/** 注册一条文件头签名（供插件扩展）。 */
		fun register(fileType: String, signature: String) {
			FILE_SIGNATURES.add(FileSignature(fileType, signature))
		}

		private fun detectByHeaders(data: ByteArray): String? {
			for (sig in FILE_SIGNATURES) {
				if (FileSignature.matches(sig, data)) {
					if (sig.fileType == "png" && isNinePatch(data)) {
						return ".9.png"
					}
					return "." + sig.fileType
				}
			}
			return null
		}

		/** 推断文件扩展名；无法识别时返回 `null`。 */
		fun detectFileExtension(data: ByteArray): String? {
			// 1. 先按文件头检测
			val extByHeaders = detectByHeaders(data)
			if (!StringUtils.isEmpty(extByHeaders)) {
				return extByHeaders
			}

			// 2. 再按可读文本检测
			val text = String(data, StandardCharsets.UTF_8)
			if (text.startsWith("-----BEGIN CERTIFICATE-----")) {
				return ".cer"
			}
			if (text.startsWith("-----BEGIN PRIVATE KEY-----")) {
				return ".key"
			}
			if (text.contains("<html>")) {
				return ".html"
			}
			val m = DOCTYPE_PATTERN.matcher(text)
			if (m.lookingAt()) {
				return "." + m.group(1).lowercase()
			}

			// 3. 最后尝试按 XML 解析，依据根标签判断
			try {
				val factory = DocumentBuilderFactory.newInstance()
				factory.setNamespaceAware(true)
				factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
				factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
				factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
				factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
				factory.setXIncludeAware(false)
				factory.setExpandEntityReferences(false)

				val builder = factory.newDocumentBuilder()
				val doc = builder.parse(ByteArrayInputStream(data))
				val rootTag = doc.getDocumentElement().getNodeName()

				if ("svg".equals(rootTag, ignoreCase = true)) {
					return ".svg"
				}
				if ("plist".equals(rootTag, ignoreCase = true)) {
					return ".plist"
				}
				if ("kml".equals(rootTag, ignoreCase = true)) {
					return ".kml"
				}
				return ".xml"
			} catch (ignored: Exception) {
				// 不是合法 XML，忽略
			}

			return null
		}

		/** 按大端序读取 4 字节整数（用于解析 PNG 分块长度/类型）。 */
		private fun readInt(data: ByteArray, offset: Int): Int = ((data[offset].toInt() and 0xFF) shl 24) or
			((data[offset + 1].toInt() and 0xFF) shl 16) or
			((data[offset + 2].toInt() and 0xFF) shl 8) or
			(data[offset + 3].toInt() and 0xFF)

		/**
		 * 判断 PNG 是否为 Android 的九宫格图（.9.png）。
		 *
		 * 依据：PNG 中若存在 `npTc`（0x6e705463）分块，即为 NinePatch。
		 * 分块结构为：4 字节长度 + 4 字节类型 + 数据 + 4 字节 CRC。
		 */
		private fun isNinePatch(data: ByteArray): Boolean {
			var offset = 8
			while (offset + 8 < data.size) {
				val chunkLength = readInt(data, offset)
				val chunkType = readInt(data, offset + 4)
				if (chunkType == 0x6e705463) { // 'npTc'
					return true
				}
				offset += 8 + chunkLength + 4 // 长度 + 类型 + 数据 + CRC
			}
			return false
		}
	}
}
