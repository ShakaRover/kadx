package jadx.cli.tools

import jadx.api.JadxArgs
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.android.TextResMapFile
import jadx.core.utils.files.FileUtils.expandDirs
import jadx.core.xmlgen.ResTableBinaryParser
import jadx.zip.ZipContent
import jadx.zip.ZipReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 把 `.arsc` 资源表转换为 “资源 id -> 资源名” 的文本映射文件。
 *
 * **做什么**：读取一个或多个 `resources.arsc`（或包含它的 `android.jar`），
 * 合并成 `res-map.txt` 格式，用于给 jadx 提供资源名映射。
 *
 * **为什么这样写**：这是一个带 `main` 的命令行小工具，`main` 加 `@JvmStatic` 保留静态入口，
 * 其余方法放进 `companion object`。
 */
class ConvertArscFile {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ConvertArscFile::class.java)

		/** 合并过程中被改写（同名不同值）的条目计数。 */
		private var rewritesCount = 0

		/** 打印用法说明。 */
		fun usage() {
			LOG.info("<res-map file> <input .arsc/android.jar files or dir>")
			LOG.info("")
			LOG.info("Note: If res-map already exists - it will be merged and updated")
		}

		/** 命令行入口。 */
		@JvmStatic
		@Throws(IOException::class)
		fun main(args: Array<String>) {
			if (args.size < 2) {
				usage()
				System.exit(1)
			}
			val inputPaths = args.map { Paths.get(it) }.toMutableList()
			val resMapFile = inputPaths.removeAt(0)
			val inputResFiles = filterAndSort(expandDirs(inputPaths))
			val resMap: MutableMap<Int, String>
			if (Files.isReadable(resMapFile)) {
				resMap = TextResMapFile.read(resMapFile).toMutableMap()
			} else {
				resMap = HashMap()
			}
			LOG.info("Input entries count: {}", resMap.size)

			@Suppress("DEPRECATION")
			val root = RootNode(JadxArgs()) // 仅作为解析上下文，并非真正需要
			val zipReader = ZipReader()
			rewritesCount = 0
			for (resFile in inputResFiles) {
				val resTableParser = ResTableBinaryParser(root, true)
				var loaded = true
				if (resFile.fileName.toString().endsWith(".jar")) {
					// 从 android.jar 中读取 resources.arsc
					zipReader.open(resFile.toFile()).use { zip: ZipContent ->
						val entry = zip.searchEntry("resources.arsc")
						if (entry == null) {
							LOG.error("Failed to load \"resources.arsc\" from {}", resFile)
							loaded = false
						} else {
							entry.inputStream.use { inputStream: InputStream ->
								resTableParser.decode(inputStream)
							}
						}
					}
				} else {
					// 从已解压的 resources.arsc 文件读取
					Files.newInputStream(resFile).use { inputStream: InputStream ->
						resTableParser.decode(inputStream)
					}
				}
				if (!loaded) {
					continue
				}
				val singleResMap = checkNotNull(resTableParser.resStorage).resourcesNames
				mergeResMaps(resMap, singleResMap)
				LOG.info("{} entries count: {}, after merge: {}", resFile.fileName, singleResMap.size, resMap.size)
			}
			LOG.info("Output entries count: {}", resMap.size)
			LOG.info("Total rewrites count: {}", rewritesCount)
			TextResMapFile.write(resMapFile, resMap)
			LOG.info("Result file size: {} B", resMapFile.toFile().length())
			LOG.info("done")
		}

		private fun filterAndSort(inputPaths: List<Path>): List<Path> = inputPaths
			.filter { path ->
				val fileName = path.fileName.toString()
				fileName.endsWith(".arsc") || fileName.endsWith(".jar")
			}
			.sorted()

		/** 把 [newResMap] 合并进 [mainResMap]；同 id 不同名视为一次改写。 */
		private fun mergeResMaps(mainResMap: MutableMap<Int, String>, newResMap: Map<Int, String>) {
			for ((id, name) in newResMap) {
				val prevName = mainResMap.put(id, name)
				if (prevName != null && name != prevName) {
					LOG.debug("Rewrite id: {} from: '{}' to: '{}'", Integer.toHexString(id), prevName, name)
					rewritesCount++
				}
			}
		}
	}
}
