package jadx.plugins.input.xapk

import jadx.api.plugins.JadxPluginContext
import jadx.core.utils.GsonUtils
import jadx.core.utils.files.FileUtils
import jadx.plugins.input.xapk.data.XApkData
import jadx.plugins.input.xapk.data.XApkManifest
import jadx.zip.IZipEntry
import jadx.zip.ZipContent
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * XApk 解包器：校验并解压 .xapk（本质是 zip），缓存已加载的数据。
 *
 * **背景**：[checkAndLoad] 检查文件是否为合法 xapk（zip + manifest.json + version==2
 * 且有 split_apks），然后把所有条目解包到插件临时目录（以源文件 md5 命名子目录），
 * 按 manifest 声明区分 apk 与资源文件。已加载结果缓存在 [loaded]，
 * unload() 时删除全部临时目录。
 */
public class XApkLoader(private val context: JadxPluginContext) {

	private val loaded = HashMap<String, XApkData>()

	/** @Nullable 不是合法 xapk 或解析失败时返回 null（并记录 warn 日志） */
	public fun checkAndLoad(inputPath: Path): XApkData? {
		val fileName = inputPath.fileName.toString()
		if (!fileName.lowercase(Locale.ROOT).endsWith(".xapk")) {
			return null
		}
		try {
			val loadedData = getLoaded(inputPath)
			if (loadedData != null) {
				return loadedData
			}
			val xapkFile = inputPath.toFile()
			if (!FileUtils.isZipFile(xapkFile)) {
				return null
			}
			return context.getZipReader().open(xapkFile).use { content ->
				val manifestEntry = content.searchEntry("manifest.json") ?: return@use null
				val manifestStr = String(manifestEntry.bytes, StandardCharsets.UTF_8)
				val xApkManifest = GsonUtils.buildGson().fromJson(manifestStr, XApkManifest::class.java)
				// splitApks 为 null 时与原 Java 行为一致：抛 NPE 被外层 catch 记录 warn 后返回 null
				if (xApkManifest.version != 2 || xApkManifest.splitApks!!.isEmpty()) {
					return@use null
				}
				// 校验完成，解包所有文件到临时目录
				val xApkData = unpackXApk(xapkFile, xApkManifest, content)
				saveLoaded(inputPath, xApkData)
				return@use xApkData
			}
		} catch (e: Exception) {
			LOG.warn("Failed to load XApk file: {}", inputPath.toAbsolutePath(), e)
			return null
		}
	}

	private fun unpackXApk(xapkFile: File, xApkManifest: XApkManifest, content: ZipContent): XApkData {
		val declaredApks = checkNotNull(xApkManifest.splitApks).map { it.file }.toSet()
		val apks = ArrayList<Path>(declaredApks.size)
		val files = ArrayList<Path>(content.entries.size)
		val dirName = FileUtils.md5Sum(xapkFile.absolutePath)
		val tmpDir = context.files().getPluginTempDir().resolve(dirName)
		FileUtils.makeDirs(tmpDir)
		for (entry in content.entries) {
			if (entry.isDirectory) {
				continue
			}
			try {
				val fileName = entry.name
				val file = tmpDir.resolve(fileName)
				FileUtils.makeDirsForFile(file)
				entry.inputStream.use { inputStream ->
					Files.copy(inputStream, file, StandardCopyOption.REPLACE_EXISTING)
				}
				if (declaredApks.contains(fileName)) {
					apks.add(file)
				} else {
					files.add(file)
				}
			} catch (e: Exception) {
				LOG.error("Failed to unpack XApk entry: {}", entry.name, e)
			}
		}
		return XApkData(xApkManifest, tmpDir, apks, files)
	}

	private fun getLoaded(inputPath: Path): XApkData? = loaded[pathToKey(inputPath)]

	private fun saveLoaded(inputPath: Path, xApkData: XApkData) {
		loaded[pathToKey(inputPath)] = xApkData
	}

	@Synchronized
	public fun unload() {
		for (data in loaded.values) {
			FileUtils.deleteDirIfExists(data.tmpDir)
		}
		loaded.clear()
	}

	private companion object {
		private val LOG = LoggerFactory.getLogger(XApkLoader::class.java)

		private fun pathToKey(path: Path): String = path.toRealPath(LinkOption.NOFOLLOW_LINKS).toString()
	}
}
