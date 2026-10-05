package kadx.core.xmlgen

import kadx.api.KadxDecompiler
import kadx.api.ResourceFile
import kadx.api.ResourcesLoader
import kadx.api.security.IKadxSecurity
import kadx.core.dex.visitors.SaveCode
import kadx.core.utils.exceptions.KadxException
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.utils.files.FileUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 把 [ResourceFile] 的内容保存到输出目录。
 *
 * 作为 [Runnable] 交给线程池执行：解码资源并递归写出资源表子文件。
 * 保存路径经过 [IKadxSecurity.isInSubDirectory] 校验，防止路径穿越攻击。
 */
class ResourcesSaver(
	decompiler: KadxDecompiler,
	private val outDir: File,
	private val resourceFile: ResourceFile,
) : Runnable {

	private val security: IKadxSecurity = decompiler.getArgs().security

	override fun run() {
		try {
			saveResources(resourceFile.loadContent())
		} catch (e: StackOverflowError) {
			LOG.warn("Failed to save resource: {}", resourceFile.getOriginalName(), e)
		} catch (e: Exception) {
			LOG.warn("Failed to save resource: {}", resourceFile.getOriginalName(), e)
		}
	}

	private fun saveResources(rc: ResContainer?) {
		if (rc == null) {
			return
		}
		if (rc.dataType == ResContainer.DataType.RES_TABLE) {
			saveToFile(rc, File(outDir, "res/values/public.xml"))
			for (subFile in rc.subFiles) {
				saveResources(subFile)
			}
		} else {
			save(rc, outDir)
		}
	}

	private fun save(rc: ResContainer, outDir: File) {
		val outFile = File(outDir, rc.fileName)
		if (!security.isInSubDirectory(outDir, outFile)) {
			LOG.error("Invalid resource name or path traversal attack detected: {}", outFile.path)
			return
		}
		saveToFile(rc, outFile)
	}

	private fun saveToFile(rc: ResContainer, outFile: File) {
		when (rc.dataType) {
			ResContainer.DataType.TEXT, ResContainer.DataType.RES_TABLE -> {
				SaveCode.save(rc.text, outFile)
				return
			}

			ResContainer.DataType.DECODED_DATA -> {
				val data = rc.decodedData
				FileUtils.makeDirsForFile(outFile)
				try {
					Files.write(outFile.toPath(), data)
				} catch (e: Exception) {
					LOG.warn("Resource '{}' not saved, got exception", rc.name, e)
				}
				return
			}

			ResContainer.DataType.RES_LINK -> {
				val resFile = rc.resLink
				FileUtils.makeDirsForFile(outFile)
				try {
					saveResourceFile(resFile, outFile)
				} catch (e: Exception) {
					LOG.warn("Resource '{}' not saved, got exception", rc.name, e)
				}
				return
			}

			else -> {
				LOG.warn("Resource '{}' not saved, unknown type", rc.name)
			}
		}
	}

	@Throws(KadxException::class)
	private fun saveResourceFile(resFile: ResourceFile, outFile: File) {
		ResourcesLoader.decodeStream(resFile) { _, inputStream ->
			val target = outFile.toPath()
			try {
				Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING)
			} catch (e: Exception) {
				Files.deleteIfExists(target) // delete partially written file
				throw KadxRuntimeException("Resource file save error", e)
			}
			null
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ResourcesSaver::class.java)
	}
}
