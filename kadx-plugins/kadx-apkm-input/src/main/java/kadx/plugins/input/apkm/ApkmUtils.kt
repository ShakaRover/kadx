package kadx.plugins.input.apkm

import kadx.core.utils.GsonUtils.buildGson
import kadx.core.utils.files.FileUtils
import kadx.zip.ZipReader
import java.io.File
import java.io.InputStreamReader

object ApkmUtils {
	fun getManifest(file: File, zipReader: ZipReader): ApkmManifest? {
		if (!FileUtils.isZipFile(file)) return null
		try {
			zipReader.open(file).use { zip ->
				val manifestEntry = zip.searchEntry("info.json") ?: return null
				return InputStreamReader(manifestEntry.inputStream).use {
					buildGson().fromJson(it, ApkmManifest::class.java)
				}
			}
		} catch (e: Exception) {
			return null
		}
	}

	fun isSupported(manifest: ApkmManifest): Boolean = manifest.apkmVersion != -1
}
