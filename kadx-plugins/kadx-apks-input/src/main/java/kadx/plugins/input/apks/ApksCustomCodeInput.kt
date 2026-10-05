package kadx.plugins.input.apks

import kadx.api.plugins.input.ICodeLoader
import kadx.api.plugins.input.KadxCodeInput
import kadx.api.plugins.utils.CommonFileUtils
import kadx.plugins.input.dex.DexInputPlugin
import kadx.zip.ZipReader
import java.io.File
import java.nio.file.Path

class ApksCustomCodeInput(
	private val dexInputPlugin: DexInputPlugin,
	private val zipReader: ZipReader,
) : KadxCodeInput {
	// 注意：接口参数必须用 java.util.List（Kotlin List 协变会编译成 List<? extends Path>，破坏 Java lambda/方法引用）
	override fun loadFiles(input: java.util.List<Path>): ICodeLoader {
		val apkFiles = mutableListOf<File>()
		for (file in input.map { it.toFile() }) {
			if (!file.name.endsWith(".apks")) continue

			// Load all files ending with .apk
			zipReader.visitEntries<Any>(file) { entry ->
				if (entry.name.endsWith(".apk")) {
					val tmpFile = entry.inputStream.use {
						CommonFileUtils.saveToTempFile(it, ".apk").toFile()
					}
					apkFiles.add(tmpFile)
				}
				null
			}
		}

		val codeLoader = dexInputPlugin.loadFiles(apkFiles.map { it.toPath() })

		apkFiles.forEach { CommonFileUtils.safeDeleteFile(it) }

		return codeLoader
	}
}
