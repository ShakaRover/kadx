package kadx.plugins.tools.utils

import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.regex.Pattern
import kotlin.jvm.JvmStatic

object PluginUtils {
	private val VERSION_LONG = Pattern.compile(".*v?(\\d+\\.\\d+\\.\\d+).*")
	private val VERSION_SHORT = Pattern.compile(".*v?(\\d+\\.\\d+).*")

	fun removePrefix(str: String, prefix: String): String = if (str.startsWith(prefix)) str.substring(prefix.length) else str

	fun downloadFile(fileUrl: String, destPath: Path) {
		try {
			val inStream = URI.create(fileUrl).toURL().openStream()
			inStream.use { Files.copy(it, destPath, REPLACE_EXISTING) }
		} catch (e: Exception) {
			throw RuntimeException("Failed to download file: $fileUrl", e)
		}
	}

	fun extractVersion(str: String): String? {
		val longMatcher = VERSION_LONG.matcher(str)
		if (longMatcher.matches()) {
			return longMatcher.group(1)
		}
		val shortMatcher = VERSION_SHORT.matcher(str)
		if (shortMatcher.matches()) {
			return shortMatcher.group(1)
		}
		return null
	}
}
