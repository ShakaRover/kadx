package jadx.plugins.input.xapk

import jadx.api.ResourceFile
import jadx.api.ResourceType
import jadx.api.ResourcesLoader
import jadx.api.plugins.CustomResourcesLoader
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.JadxCodeInput
import jadx.api.plugins.input.data.impl.EmptyCodeLoader
import jadx.core.utils.files.FileUtils
import jadx.plugins.input.dex.DexInputPlugin
import jadx.plugins.input.xapk.data.XApkData
import java.io.File
import java.nio.file.Path

/**
 * XApk 的代码输入 + 自定义资源加载器。
 *
 * **背景**：[loadFiles] 把 .xapk 解包出的 split apk 交给 DexInputPlugin 解析；
 * [load] 把 xapk 内的其他文件按原始相对路径注册为资源（zip 文件走默认解压逻辑）。
 */
public class XApkCustomInput(
	private val context: JadxPluginContext,
	private val xApkLoader: XApkLoader,
) : JadxCodeInput,
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
