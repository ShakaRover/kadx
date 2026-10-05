package kadx.plugins.input.xapk

import kadx.api.ResourceFile
import kadx.api.ResourceType
import kadx.api.ResourcesLoader
import kadx.api.plugins.CustomResourcesLoader
import kadx.api.plugins.KadxPluginContext
import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.input.data.impl.EmptyCodeLoader
import kadx.core.utils.files.FileUtils
import kadx.plugins.input.dex.DexInputPlugin
import kadx.plugins.input.xapk.data.XApkData
import java.io.File
import java.nio.file.Path

/**
 * XApk 的代码输入 + 自定义资源加载器。
 *
 * **背景**：[loadFiles] 把 .xapk 解包出的 split apk 交给 DexInputPlugin 解析；
 * [load] 把 xapk 内的其他文件按原始相对路径注册为资源（zip 文件走默认解压逻辑）。
 */
public class XApkCustomInput(
	private val context: KadxPluginContext,
	private val xApkLoader: XApkLoader,
) : KadxCodeInput,
	CustomResourcesLoader {

	override fun loadFiles(input: java.util.List<Path>): ICodeLoader {
		val apks = mutableListOf<Path>()
		for (inputPath in input) {
			val data = xApkLoader.checkAndLoad(inputPath)
			if (data != null) {
				apks.addAll(data.apks)
			}
		}
		if (apks.isEmpty()) {
			return EmptyCodeLoader.INSTANCE
		}
		val dexInputPlugin: DexInputPlugin = context.plugins().getInstance(DexInputPlugin::class.java)
		return dexInputPlugin.loadFiles(apks)
	}

	// 覆写 Java 接口：参数用 Kotlin MutableList（柔性类型匹配，且需要 add()）
	override fun load(loader: ResourcesLoader, list: MutableList<ResourceFile>, file: File): Boolean {
		val xApkData = xApkLoader.checkAndLoad(file.toPath()) ?: return false
		for (apkPath in xApkData.apks) {
			loader.defaultLoadFile(list, apkPath.toFile(), apkPath.fileName.toString() + "/")
		}
		for (filePath in xApkData.files) {
			val innerFile = filePath.toFile()
			val relativePath = xApkData.tmpDir.relativize(filePath).toString()
			if (FileUtils.isZipFile(innerFile)) {
				// zip 文件由默认加载器解压
				loader.defaultLoadFile(list, innerFile, relativePath + "/")
			} else {
				// 在临时目录中创建资源，但名称使用相对 xapk 根目录的路径
				val type = ResourceType.getFileType(relativePath)
				val resFile = ResourceFile.createResourceFile(context.getDecompiler(), innerFile, type)
				resFile.setDeobfName(relativePath)
				list.add(resFile)
			}
		}
		return true
	}

	override fun close() {
		// 无需清理（临时目录由 XApkLoader.unload 负责）
	}
}
