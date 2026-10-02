package jadx.api

import jadx.api.impl.SimpleCodeInfo
import jadx.api.plugins.CustomResourcesLoader
import jadx.api.plugins.resources.IResContainerFactory
import jadx.api.plugins.resources.IResTableParserProvider
import jadx.api.plugins.resources.IResourcesLoader
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils
import jadx.core.utils.android.Res9patchStreamDecoder
import jadx.core.utils.exceptions.JadxException
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.core.xmlgen.BinaryXMLParser
import jadx.core.xmlgen.IResTableParser
import jadx.core.xmlgen.ResContainer
import jadx.core.xmlgen.ResTableBinaryParserProvider
import jadx.zip.IZipEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.ArrayList

/**
 * 资源加载器：把输入文件（apk/zip/jar 等）解析成 [ResourceFile] 列表，并负责解码资源内容。
 *
 * 公共 API（jadx-cli / jadx-gui / 插件都会使用）。静态方法 [decodeStream] / [loadToCodeWriter]
 * 用 companion + `@JvmStatic` 保持 Java 调用写法不变。
 */
class ResourcesLoader internal constructor(
	private val decompiler: JadxDecompiler,
) : IResourcesLoader {

	private val resTableParserProviders = ArrayList<IResTableParserProvider>()
	private val resContainerFactories = ArrayList<IResContainerFactory>()

	private var binaryXmlParser: BinaryXMLParser? = null

	init {
		// 注册默认的二进制资源表解析器（与原 Java 构造器行为一致）
		resTableParserProviders.add(ResTableBinaryParserProvider())
	}

	/** 资源解码回调：把资源流解码成任意类型 [T]。 */
	fun interface ResourceDecoder<T> {
		@Throws(IOException::class)
		fun decode(size: Long, `is`: InputStream): T
	}

	internal fun load(root: RootNode): List<ResourceFile> {
		init(root)
		val inputFiles = decompiler.getArgs().inputFiles
		val list = ArrayList<ResourceFile>(inputFiles.size)
		for (file in inputFiles) {
			loadFile(list, file)
		}
		return list
	}

	private fun init(root: RootNode) {
		for (resTableParserProvider in resTableParserProviders) {
			try {
				resTableParserProvider.init(root)
			} catch (e: Exception) {
				throw JadxRuntimeException("Failed to init res table provider: " + resTableParserProvider)
			}
		}
		for (resContainerFactory in resContainerFactories) {
			try {
				resContainerFactory.init(root)
			} catch (e: Exception) {
				throw JadxRuntimeException("Failed to init res container factory: " + resContainerFactory)
			}
		}
	}

	override fun addResContainerFactory(resContainerFactory: IResContainerFactory) {
		resContainerFactories.add(resContainerFactory)
	}

	override fun addResTableParserProvider(resTableParserProvider: IResTableParserProvider) {
		resTableParserProviders.add(resTableParserProvider)
	}

	private fun loadContent(resFile: ResourceFile, inputStream: InputStream): ResContainer {
		for (customFactory in resContainerFactories) {
			val resContainer = customFactory.create(resFile, inputStream)
			if (resContainer != null) {
				return resContainer
			}
		}
		return when (resFile.getType()) {
			ResourceType.MANIFEST, ResourceType.XML -> {
				val content = loadBinaryXmlParser().parse(inputStream)
				ResContainer.textResource(resFile.getDeobfName(), content)
			}

			ResourceType.ARSC -> decodeTable(resFile, inputStream).decodeFiles()

			ResourceType.IMG -> decodeImage(resFile, inputStream)

			else -> ResContainer.resourceFileLink(resFile)
		}
	}

	@Throws(IOException::class)
	fun decodeTable(resFile: ResourceFile, `is`: InputStream): IResTableParser {
		if (resFile.getType() != ResourceType.ARSC) {
			throw IllegalArgumentException("Unexpected resource type for decode: " + resFile.getType() + ", expect '.pb'/'.arsc'")
		}
		var parser: IResTableParser? = null
		for (provider in resTableParserProviders) {
			parser = provider.getParser(resFile)
			if (parser != null) {
				break
			}
		}
		if (parser == null) {
			throw JadxRuntimeException("Unknown type of resource file: " + resFile.getOriginalName())
		}
		parser.setBaseFileName(resFile.getDeobfName())
		parser.decode(`is`)
		return parser
	}

	private fun decodeImage(rf: ResourceFile, inputStream: InputStream): ResContainer {
		val name = rf.getDeobfName()
		if (name.endsWith(".9.png")) {
			try {
				ByteArrayOutputStream().use { os ->
					val decoder = Res9patchStreamDecoder()
					if (decoder.decode(inputStream, os)) {
						return ResContainer.decodedData(rf.getDeobfName(), os.toByteArray())
					}
				}
			} catch (e: Exception) {
				LOG.error("Failed to decode 9-patch png image, path: {}", name, e)
			}
		}
		return ResContainer.resourceFileLink(rf)
	}

	private fun loadFile(list: MutableList<ResourceFile>, file: File?) {
		if (file == null || file.isDirectory) {
			return
		}

		// 优先尝试自定义资源加载器
		for (loader in decompiler.getCustomResourcesLoaders()) {
			if (loader.load(this, list, file)) {
				LOG.debug("Custom loader used for {}", file.absolutePath)
				return
			}
		}

		// 没有自定义加载器能处理时，使用默认加载逻辑
		defaultLoadFile(list, file, "")
	}

	fun defaultLoadFile(list: MutableList<ResourceFile>, file: File, subDir: String) {
		if (FileUtils.isZipFile(file)) {
			try {
				val zipContent = decompiler.getZipReader().open(file)
				// 现在不要关闭 zip，条目内容稍后才会读取
				decompiler.addCloseable(zipContent)
				for (entry in zipContent.getEntries()) {
					addEntry(list, file, entry, subDir)
				}
			} catch (e: Exception) {
				throw RuntimeException("Failed to open zip file: " + file.absolutePath, e)
			}
		} else {
			val type = ResourceType.getFileType(file.absolutePath)
			list.add(ResourceFile.createResourceFile(decompiler, file, type))
		}
	}

	fun addEntry(list: MutableList<ResourceFile>, zipFile: File, entry: IZipEntry, subDir: String) {
		if (entry.isDirectory()) {
			return
		}
		val name = entry.getName()
		val type = ResourceType.getFileType(name)
		val rf = ResourceFile.createResourceFile(decompiler, subDir + name, type)
		if (rf != null) {
			rf.setZipEntry(entry)
			list.add(rf)
		}
	}

	@Synchronized
	private fun loadBinaryXmlParser(): BinaryXMLParser {
		var parser = binaryXmlParser
		if (parser == null) {
			parser = BinaryXMLParser(checkNotNull(decompiler.getRoot()))
			binaryXmlParser = parser
		}
		return parser
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ResourcesLoader::class.java)

		/** 从资源文件读取一个流并交给 [decoder] 解码；读失败统一包装成 [JadxException]。 */
		@JvmStatic
		@Throws(JadxException::class)
		fun <T> decodeStream(rf: ResourceFile, decoder: ResourceDecoder<T>): T = try {
			val zipEntry = rf.getZipEntry()
			if (zipEntry != null) {
				zipEntry.getInputStream().use { inputStream ->
					decoder.decode(zipEntry.getUncompressedSize(), inputStream)
				}
			} else {
				val file = File(rf.getOriginalName())
				BufferedInputStream(FileInputStream(file)).use { inputStream ->
					decoder.decode(file.length(), inputStream)
				}
			}
		} catch (e: Exception) {
			throw JadxException("Error decode: " + rf.getOriginalName(), e)
		}

		/** 解码资源内容；失败时返回一段包含堆栈的错误文本资源。 */
		@JvmStatic
		internal fun loadContent(jadxRef: JadxDecompiler, rf: ResourceFile): ResContainer {
			try {
				val resLoader = jadxRef.getResourcesLoader()
				return decodeStream(rf) { _, inputStream -> resLoader.loadContent(rf, inputStream) }
			} catch (e: JadxException) {
				LOG.error("Decode error", e)
				val cw = checkNotNull(jadxRef.getRoot()).makeCodeWriter()
				cw.add("Error decode ").add(rf.getType().toString().lowercase())
				Utils.appendStackTrace(cw, e.cause)
				return ResContainer.textResource(rf.getDeobfName(), cw.finish())
			}
		}

		/** 读取输入流并包装成代码信息（默认 UTF-8）。 */
		@JvmStatic
		@Throws(IOException::class)
		fun loadToCodeWriter(`is`: InputStream): ICodeInfo = loadToCodeWriter(`is`, StandardCharsets.UTF_8)

		/** 读取输入流并按 [charset] 解码为代码信息。 */
		@JvmStatic
		@Throws(IOException::class)
		fun loadToCodeWriter(`is`: InputStream, charset: Charset): ICodeInfo {
			val baos = ByteArrayOutputStream(FileUtils.READ_BUFFER_SIZE)
			FileUtils.copyStream(`is`, baos)
			return SimpleCodeInfo(baos.toString(charset))
		}
	}
}
