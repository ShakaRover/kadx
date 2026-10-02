package jadx.api

import jadx.api.resources.ResourceContentType
import jadx.api.resources.ResourceContentType.CONTENT_BINARY
import jadx.api.resources.ResourceContentType.CONTENT_TEXT
import jadx.api.resources.ResourceContentType.CONTENT_UNKNOWN
import jadx.core.utils.exceptions.JadxRuntimeException
import java.util.HashMap
import java.util.Locale

/**
 * 资源文件类型：根据扩展名把资源归类，并记录其内容类型（文本/二进制/未知）。
 *
 * 这是公共 API，枚举常量名与顺序必须保持不变（jadx-cli / jadx-gui / 插件都会使用）。
 *
 * 实现说明：原 Java 用静态代码块构建“扩展名 -> 类型”的索引表；Kotlin 放在 companion object
 * 的 `init` 中。枚举常量会先于 companion 初始化，因此 `values()` 在 init 时已可用。
 */
enum class ResourceType(val contentType: ResourceContentType, vararg val exts: String) {
	CODE(CONTENT_BINARY, ".dex", ".jar", ".class"),
	XML(CONTENT_TEXT, ".xml"),
	ARSC(CONTENT_TEXT, ".arsc"),
	APK(CONTENT_BINARY, ".apk", ".apkm", ".apks"),
	FONT(CONTENT_BINARY, ".ttf", ".ttc", ".otf"),
	IMG(CONTENT_BINARY, ".png", ".gif", ".jpg", ".jpeg", ".webp", ".bmp", ".tiff"),
	ARCHIVE(
		CONTENT_BINARY,
		".zip", ".rar", ".7zip", ".7z", ".arj", ".tar", ".gzip", ".bzip", ".bzip2", ".cab", ".cpio", ".ar", ".gz",
		".tgz", ".bz2",
	),
	VIDEOS(CONTENT_BINARY, ".mp4", ".mkv", ".webm", ".avi", ".flv", ".3gp"),
	SOUNDS(CONTENT_BINARY, ".aac", ".ogg", ".opus", ".mp3", ".wav", ".wma", ".mid", ".midi"),
	JSON(CONTENT_TEXT, ".json"),
	TEXT(CONTENT_TEXT, ".txt", ".ini", ".conf", ".yaml", ".properties", ".js", ".java", ".kt", ".md"),
	HTML(CONTENT_TEXT, ".html", ".htm"),
	LIB(CONTENT_BINARY, ".so"),
	MANIFEST(CONTENT_TEXT),
	UNKNOWN_BIN(CONTENT_BINARY, ".bin"),
	UNKNOWN(CONTENT_UNKNOWN),
	;

	companion object {
		/** 扩展名（含前导点，如 `.xml`）到资源类型的索引。 */
		private val EXT_MAP: MutableMap<String, ResourceType> = HashMap()

		init {
			// 构建索引；如果两个枚举声明了相同的扩展名，说明配置错误，直接抛异常暴露问题
			for (type in values()) {
				for (ext in type.exts) {
					val prev = EXT_MAP.put(ext, type)
					if (prev != null) {
						throw JadxRuntimeException("Duplicate extension in ResourceType: $ext")
					}
				}
			}
		}

		/**
		 * 根据文件名推断资源类型。
		 *
		 * 特殊处理：
		 * - `resources.pb` 视为 [ARSC]；
		 * - 名为 `AndroidManifest.xml` 的 XML 视为 [MANIFEST]；
		 * - 无法识别时返回 [UNKNOWN]。
		 *
		 * 用 `@JvmStatic` 保持 Java 调用方写法 `ResourceType.getFileType(...)` 不变。
		 */
		@JvmStatic
		fun getFileType(fileName: String): ResourceType {
			if (fileName.endsWith("/resources.pb")) {
				return ARSC
			}
			val dot = fileName.lastIndexOf('.')
			if (dot != -1) {
				// 原 Java 用 Locale.ROOT 转小写，避免土耳其语等区域设置导致 "I" 转换异常，这里保持一致
				val ext = fileName.substring(dot).lowercase(Locale.ROOT)
				val resType = EXT_MAP[ext]
				if (resType != null) {
					if (resType === XML && fileName == "AndroidManifest.xml") {
						return MANIFEST
					}
					return resType
				}
			}
			return UNKNOWN
		}
	}
}
