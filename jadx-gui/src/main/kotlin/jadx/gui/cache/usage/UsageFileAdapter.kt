package jadx.gui.cache.usage

import jadx.api.plugins.input.data.IMethodRef
import jadx.api.usage.IUsageInfoData
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.Utils
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.utils.files.FileUtils
import jadx.gui.cache.code.disk.adapters.DataAdapterHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.HashMap
import java.util.HashSet

/**
 * usage 数据的磁盘文件读写适配器。
 *
 * **做什么**：把 [IUsageInfoData] 序列化为自定义二进制格式（非 JSON），或从磁盘反序列化。
 * 文件头为 ASCII 字符串 `jadx.usage`，随后是版本号、输入文件哈希与数据体。
 *
 * **格式契约（持久化格式，禁止随意变更）**：
 * - 头部：`jadx.usage`（10 字节）+ `int` 版本号 [USAGE_DATA_VERSION] + UTF 输入哈希；
 * - 之后依次是类信息、未解析方法签名池、方法信息、以及各类 / 方法 / 字段的 usage 明细；
 * - 整数使用 ULEB128（见 [DataAdapterHelper]），字符串使用 `writeUTF`。
 *
 * **线程模型**：加载与保存均用 `@Synchronized` 串行化，保持原 Java 静态同步方法语义。
 */
internal class UsageFileAdapter : DataAdapterHelper() {

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(UsageFileAdapter::class.java)

		/** usage 数据格式版本；版本不匹配时缓存文件会被删除并重新分析。 */
		private const val USAGE_DATA_VERSION = 3

		/** 文件魔数头。 */
		private val JADX_USAGE_HEADER = "jadx.usage".toByteArray(StandardCharsets.US_ASCII)

		/** 从磁盘加载 usage 数据；文件不存在 / 版本不符 / 输入变化时返回 `null`。 */
		@Synchronized
		fun load(root: RootNode, usageFile: Path, inputs: List<File>): RawUsageData? {
			if (!Files.isRegularFile(usageFile)) {
				return null
			}
			val start = System.currentTimeMillis()
			try {
				DataInputStream(BufferedInputStream(Files.newInputStream(usageFile))).use { input ->
					input.skipBytes(JADX_USAGE_HEADER.size)
					val dataVersion = input.readInt()
					if (dataVersion != USAGE_DATA_VERSION) {
						LOG.debug("Found old usage data format")
						FileUtils.deleteFileIfExists(usageFile)
						return null
					}
					val inputsHash = buildInputsHash(inputs)
					val fileInputsHash = input.readUTF()
					if (inputsHash != fileInputsHash) {
						LOG.debug("Found usage data with different inputs hash")
						FileUtils.deleteFileIfExists(usageFile)
						return null
					}
					val data = readData(root, input)
					if (LOG.isDebugEnabled) {
						LOG.debug(
							"Loaded usage data from disk cache, classes count: {}, time: {}ms, file: {}",
							data.clsMap.size,
							System.currentTimeMillis() - start,
							usageFile,
						)
					}
					return data
				}
			} catch (e: Exception) {
				try {
					FileUtils.deleteFileIfExists(usageFile)
				} catch (ex: IOException) {
					// 删除失败无需处理，下面仍会记录原始异常
				}
				LOG.error("Failed to load usage data file", e)
				return null
			}
		}

		/** 把 usage 数据保存到磁盘。 */
		@Synchronized
		fun save(data: IUsageInfoData, usageFile: Path, inputs: List<File>) {
			val start = System.currentTimeMillis()
			FileUtils.makeDirsForFile(usageFile)
			val inputsHash = buildInputsHash(inputs)
			val usageData = RawUsageData()
			data.visitUsageData(CollectUsageData(usageData))
			try {
				Files.newOutputStream(usageFile, WRITE, CREATE, TRUNCATE_EXISTING).use { fileOutput ->
					DataOutputStream(BufferedOutputStream(fileOutput)).use { out ->
						out.write(JADX_USAGE_HEADER)
						out.writeInt(USAGE_DATA_VERSION)
						out.writeUTF(inputsHash)
						writeData(out, usageData)
					}
				}
			} catch (e: Exception) {
				LOG.error("Failed to save usage data file", e)
				try {
					FileUtils.deleteFileIfExists(usageFile)
				} catch (ex: IOException) {
					LOG.error("Failed to delete usage data file: {}", usageFile, ex)
				}
			}
			if (LOG.isDebugEnabled) {
				LOG.debug("Usage data saved, time: {}ms, file: {}", System.currentTimeMillis() - start, usageFile)
			}
		}

		/** 反序列化数据体。 */
		private fun readData(root: RootNode, input: DataInputStream): RawUsageData {
			val data = RawUsageData()
			val clsCount = DataAdapterHelper.readUVInt(input)
			val clsWithoutDataCount = DataAdapterHelper.readUVInt(input)

			// 类信息：前 clsCount 个类有自己的 usage 数据，后面的类只是名字池
			val clsNames = arrayOfNulls<String>(clsCount + clsWithoutDataCount)
			val classes = arrayOfNulls<ClsUsageData>(clsCount)
			var c = 0
			for (i in 0 until clsCount) {
				val clsRawName = input.readUTF()
				classes[i] = data.getClassData(clsRawName)
				clsNames[c] = clsRawName
				c++
			}
			for (i in 0 until clsWithoutDataCount) {
				clsNames[c] = input.readUTF()
				c++
			}
			val uClsCount = DataAdapterHelper.readUVInt(input)
			val uClsNames = arrayOfNulls<String>(uClsCount)
			for (i in 0 until uClsCount) {
				uClsNames[i] = input.readUTF()
			}

			// 方法信息
			val mthCount = DataAdapterHelper.readUVInt(input)
			val methods = arrayOfNulls<MthRef>(mthCount)
			for (i in 0 until mthCount) {
				val clsId = DataAdapterHelper.readUVInt(input)
				val mthShortId = input.readUTF()
				val cls = checkNotNull(classes[clsId])
				val mthRef = MthRef(cls.rawName, mthShortId)
				cls.mthUsage[mthShortId] = MthUsageData(mthRef)
				methods[i] = mthRef
			}

			// 未解析方法信息（只有签名）
			val uMthCount = DataAdapterHelper.readUVInt(input)
			val unresolvedMethods = arrayOfNulls<IMethodRef>(uMthCount)
			for (i in 0 until uMthCount) {
				val clsId = DataAdapterHelper.readUVInt(input)
				val name = input.readUTF()
				val returnType = input.readUTF()
				val argCount = DataAdapterHelper.readUVInt(input)
				val args = ArrayList<String>(argCount)
				for (j in 0 until argCount) {
					args.add(input.readUTF())
				}
				unresolvedMethods[i] = CachedMethodRef(checkNotNull(uClsNames[clsId]), name, returnType, args)
			}

			// usage 明细
			for (i in 0 until clsCount) {
				val cls = data.getClassData(checkNotNull(clsNames[i]))
				cls.clsDeps = readClsList(input, clsNames)
				cls.clsUsage = readClsList(input, clsNames)
				cls.clsUseInMth = readMthList(input, methods)

				val mCount = DataAdapterHelper.readUVInt(input)
				for (m in 0 until mCount) {
					val mthRef = checkNotNull(methods[DataAdapterHelper.readUVInt(input)])
					val mthUsageData = checkNotNull(cls.mthUsage[mthRef.shortId])
					mthUsageData.usage = readMthList(input, methods)
					mthUsageData.uses = readMthList(input, methods)
					mthUsageData.unresolvedUsage = readUnresolvedMthList(input, unresolvedMethods)
					mthUsageData.callsSelf = input.readBoolean()
				}
				val fCount = DataAdapterHelper.readUVInt(input)
				for (f in 0 until fCount) {
					val fldShortId = input.readUTF()
					val fldUsageData = cls.fldUsage.getOrPut(fldShortId) {
						FldUsageData(FldRef(cls.rawName, fldShortId))
					}
					fldUsageData.usage = readMthList(input, methods)
				}
			}
			return data
		}

		/** 序列化数据体。 */
		private fun writeData(out: DataOutputStream, usageData: RawUsageData) {
			val clsMap = HashMap<String, Int>()
			val mthMap = HashMap<MthRef, Int>()
			val uMthMap = HashMap<IMethodRef, Int>()
			val clsDataMap = usageData.clsMap

			val uClsMap = HashMap<String, Int>()
			val unresolvedMethods = ArrayList<IMethodRef>()
			for (classUsageData in clsDataMap.values) {
				for (methodUsageData in classUsageData.mthUsage.values) {
					val unresolvedUsageList = methodUsageData.unresolvedUsage
					if (unresolvedUsageList != null) {
						unresolvedMethods.addAll(unresolvedUsageList)
					}
				}
			}

			val classes = ArrayList(clsDataMap.keys)
			classes.sort()
			val classesWithoutData = usageData.classesWithoutData

			// 未解析方法涉及到的类名池
			val uClsNames = HashSet<String>()
			for (uMthRef in unresolvedMethods) {
				uClsNames.add(uMthRef.parentClassType)
			}
			val uClsList = ArrayList(uClsNames)
			uClsList.sort()

			// 类信息
			DataAdapterHelper.writeUVInt(out, classes.size)
			DataAdapterHelper.writeUVInt(out, classesWithoutData.size)
			var i = 0
			for (cls in classes) {
				out.writeUTF(cls)
				clsMap[cls] = i
				i++
			}
			for (cls in classesWithoutData) {
				out.writeUTF(cls)
				clsMap[cls] = i
				i++
			}

			DataAdapterHelper.writeUVInt(out, uClsList.size)
			var u = 0
			for (cls in uClsList) {
				out.writeUTF(cls)
				uClsMap[cls] = u
				u++
			}

			// 方法信息
			val methods = ArrayList<MthRef>()
			for (c in clsDataMap.values) {
				for (mthData in c.mthUsage.values) {
					methods.add(mthData.mthRef)
				}
			}
			DataAdapterHelper.writeUVInt(out, methods.size)
			var j = 0
			for (mth in methods) {
				DataAdapterHelper.writeUVInt(out, checkNotNull(clsMap[mth.cls]))
				out.writeUTF(mth.shortId)
				mthMap[mth] = j
				j++
			}

			// 未解析方法信息
			DataAdapterHelper.writeUVInt(out, unresolvedMethods.size)
			var k = 0
			for (uMthRef in unresolvedMethods) {
				DataAdapterHelper.writeUVInt(out, checkNotNull(uClsMap[uMthRef.parentClassType]))
				out.writeUTF(uMthRef.name)
				out.writeUTF(uMthRef.returnType)
				val args = uMthRef.argTypes
				DataAdapterHelper.writeUVInt(out, args.size)
				for (arg in args) {
					out.writeUTF(arg)
				}
				uMthMap[uMthRef] = k
				k++
			}

			// usage 明细
			for (cls in classes) {
				val clsData = checkNotNull(clsDataMap[cls])
				writeClsList(out, clsMap, clsData.clsDeps)
				writeClsList(out, clsMap, clsData.clsUsage)
				writeMthList(out, mthMap, clsData.clsUseInMth)

				DataAdapterHelper.writeUVInt(out, clsData.mthUsage.size)
				for (mthData in clsData.mthUsage.values) {
					DataAdapterHelper.writeUVInt(out, checkNotNull(mthMap[mthData.mthRef]))
					writeMthList(out, mthMap, mthData.usage)
					writeMthList(out, mthMap, mthData.uses)
					writeUnresolvedMthList(out, uMthMap, mthData.unresolvedUsage)
					out.writeBoolean(mthData.callsSelf)
				}

				DataAdapterHelper.writeUVInt(out, clsData.fldUsage.size)
				for (fldData in clsData.fldUsage.values) {
					out.writeUTF(fldData.fldRef.shortId)
					writeMthList(out, mthMap, fldData.usage)
				}
			}
		}

		/** 读取类名索引列表（索引指向 [classes] 池）。 */
		private fun readClsList(input: DataInputStream, classes: Array<String?>): List<String> {
			val count = DataAdapterHelper.readUVInt(input)
			if (count == 0) {
				return emptyList()
			}
			val list = ArrayList<String>(count)
			for (i in 0 until count) {
				list.add(checkNotNull(classes[DataAdapterHelper.readUVInt(input)]))
			}
			return list
		}

		/** 写出类名索引列表。 */
		private fun writeClsList(out: DataOutputStream, clsMap: Map<String, Int>, clsList: List<String>?) {
			if (Utils.isEmpty(clsList)) {
				DataAdapterHelper.writeUVInt(out, 0)
				return
			}
			val list = checkNotNull(clsList)
			DataAdapterHelper.writeUVInt(out, list.size)
			for (cls in list) {
				val clsId = clsMap[cls] ?: throw JadxRuntimeException("Unknown class in usage: $cls")
				DataAdapterHelper.writeUVInt(out, clsId)
			}
		}

		/** 读取方法引用索引列表（索引指向 [methods] 池）。 */
		private fun readMthList(input: DataInputStream, methods: Array<MthRef?>): List<MthRef> {
			val count = DataAdapterHelper.readUVInt(input)
			if (count == 0) {
				return emptyList()
			}
			val list = ArrayList<MthRef>(count)
			for (i in 0 until count) {
				list.add(checkNotNull(methods[DataAdapterHelper.readUVInt(input)]))
			}
			return list
		}

		/** 写出方法引用索引列表。 */
		private fun writeMthList(out: DataOutputStream, mthMap: Map<MthRef, Int>, mthList: List<MthRef>?) {
			if (Utils.isEmpty(mthList)) {
				DataAdapterHelper.writeUVInt(out, 0)
				return
			}
			val list = checkNotNull(mthList)
			DataAdapterHelper.writeUVInt(out, list.size)
			for (mth in list) {
				DataAdapterHelper.writeUVInt(out, checkNotNull(mthMap[mth]))
			}
		}

		/** 读取未解析方法引用索引列表。 */
		private fun readUnresolvedMthList(input: DataInputStream, methods: Array<IMethodRef?>): List<IMethodRef> {
			val count = DataAdapterHelper.readUVInt(input)
			if (count == 0) {
				return emptyList()
			}
			val list = ArrayList<IMethodRef>(count)
			for (i in 0 until count) {
				list.add(checkNotNull(methods[DataAdapterHelper.readUVInt(input)]))
			}
			return list
		}

		/** 写出未解析方法引用索引列表。 */
		private fun writeUnresolvedMthList(out: DataOutputStream, uMthMap: Map<IMethodRef, Int>, mthList: List<IMethodRef>?) {
			if (Utils.isEmpty(mthList)) {
				DataAdapterHelper.writeUVInt(out, 0)
				return
			}
			val list = checkNotNull(mthList)
			DataAdapterHelper.writeUVInt(out, list.size)
			for (mth in list) {
				DataAdapterHelper.writeUVInt(out, checkNotNull(uMthMap[mth]))
			}
		}

		/** 计算输入文件哈希（忽略 `.jadx.kts` 脚本文件）。 */
		private fun buildInputsHash(inputs: List<File>): String {
			val paths = inputs
				.filter { !it.name.endsWith(".jadx.kts") }
				.map { it.toPath() }
			return FileUtils.buildInputsHash(paths)
		}
	}
}
