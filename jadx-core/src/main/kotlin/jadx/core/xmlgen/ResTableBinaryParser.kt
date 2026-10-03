package jadx.core.xmlgen

import jadx.api.ICodeInfo
import jadx.api.args.ResourceNameSource
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.IFieldInfoRef
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.BetterName
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.xmlgen.entry.EntryConfig
import jadx.core.xmlgen.entry.RawNamedValue
import jadx.core.xmlgen.entry.RawValue
import jadx.core.xmlgen.entry.ResourceEntry
import jadx.core.xmlgen.entry.ValuesParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.HashSet
import java.util.StringJoiner

/**
 * 二进制资源表（`resources.arsc`）解析器。
 *
 * 解析 `ResTable` chunk：包、类型、配置、条目，产出 [ResourceStorage]；
 * 再通过 [ResXmlGen] 渲染成 `res/values/` 下的 XML 文件。
 *
 * **Kotlin 转换说明**：
 * - 基类字段 `is` 重命名为 [input]；
 * - 静态工厂 [getBetterName] 保留为 companion；
 * - 私有嵌套类 [PackageChunk] / [EntryOffset] 保持原结构。
 */
class ResTableBinaryParser @JvmOverloads constructor(
	private val root: RootNode,
	private val useRawResName: Boolean = false,
) : CommonBinaryParser(),
	IResTableParser {

	/**
	 * No renaming, pattern checking or name generation. Required for res-map.txt building
	 */
	private class PackageChunk(
		val id: Int,
		val name: String,
		val typeStrings: BinaryXMLStrings?,
		val keyStrings: BinaryXMLStrings?,
	)

	override var resStorage: ResourceStorage? = null
	override var strings: BinaryXMLStrings? = null
	private var baseFileName = ""

	override fun setBaseFileName(fileName: String) {
		this.baseFileName = fileName
	}

	@Throws(IOException::class)
	override fun decode(inputStream: InputStream) {
		val start = System.currentTimeMillis()
		input = ParserStream(BufferedInputStream(inputStream, 32768))
		val storage = ResourceStorage(root.getArgs().security)
		resStorage = storage
		decodeTableChunk()
		storage.finish()
		if (LOG.isDebugEnabled) {
			LOG.debug(
				"Resource table parsed: size: {}, time: {}ms",
				storage.size(),
				System.currentTimeMillis() - start,
			)
		}
	}

	override fun decodeFiles(): ResContainer {
		val storage = checkNotNull(resStorage)
		val vp = ValuesParser(strings, storage.resourcesNames)
		val resGen = ResXmlGen(storage, vp, root.initManifestAttributes())

		val content: ICodeInfo = XmlGenUtils.makeXmlDump(root.makeCodeWriter(), storage)
		val xmlFiles = resGen.makeResourcesXml(root.getArgs())
		return ResContainer.resourceTable(baseFileName, xmlFiles, content)
	}

	@Throws(IOException::class)
	private fun decodeTableChunk() {
		input.checkInt16(ParserConstants.RES_TABLE_TYPE, "Not a table chunk")
		input.checkInt16(0x000c, "Unexpected table header size")
		val size = input.readInt32()
		val pkgCount = input.readInt32()

		var pkgNum = 0
		while (input.pos < size.toLong()) {
			val chuckStart = input.pos
			val type = input.readInt16()
			val headerSize = input.readInt16()
			val chunkSize = input.readUInt32()
			val chunkEnd = chuckStart + chunkSize
			when (type) {
				ParserConstants.RES_NULL_TYPE -> {
					// skip
				}

				ParserConstants.RES_STRING_POOL_TYPE -> strings = parseStringPoolNoSize(chuckStart, chunkEnd)

				ParserConstants.RES_TABLE_PACKAGE_TYPE -> {
					parsePackage(chuckStart, headerSize, chunkEnd)
					pkgNum++
				}
			}
			input.skipToPos(chunkEnd, "Skip to table chunk end")
		}
		if (pkgNum != pkgCount) {
			LOG.warn("Unexpected package chunks, read: {}, expected: {}", pkgNum, pkgCount)
		}
	}

	@Throws(IOException::class)
	private fun parsePackage(pkgChunkStart: Long, headerSize: Int, pkgChunkEnd: Long) {
		if (headerSize < 0x011c) {
			die("Package header size too small")
			return
		}
		val id = input.readInt32()
		val name = input.readString16Fixed(128)
		val typeStringsOffset = pkgChunkStart + input.readInt32()
		val lastPublicType = input.readInt32()
		val keyStringsOffset = pkgChunkStart + input.readInt32()
		val lastPublicKey = input.readInt32()
		if (headerSize >= 0x0120) {
			val typeIdOffset = input.readInt32()
		}
		input.skipToPos(pkgChunkStart + headerSize, "package header end")

		var typeStrings: BinaryXMLStrings? = null
		if (typeStringsOffset != 0L) {
			input.skipToPos(typeStringsOffset, "Expected typeStrings string pool")
			typeStrings = parseStringPool()
		}
		var keyStrings: BinaryXMLStrings? = null
		if (keyStringsOffset != 0L) {
			input.skipToPos(keyStringsOffset, "Expected keyStrings string pool")
			keyStrings = parseStringPool()
		}

		val pkg = PackageChunk(id, name, typeStrings, keyStrings)
		checkNotNull(resStorage).appPackage = name

		while (input.pos < pkgChunkEnd) {
			val chunkStart = input.pos
			val type = input.readInt16()
			LOG.trace("res package chunk start at {} type {}", chunkStart, type)
			when (type) {
				ParserConstants.RES_NULL_TYPE -> LOG.info("Null chunk type encountered at offset {}", chunkStart)

				ParserConstants.RES_TABLE_TYPE_TYPE -> parseTypeChunk(chunkStart, pkg)

				ParserConstants.RES_TABLE_TYPE_SPEC_TYPE -> parseTypeSpecChunk(chunkStart)

				ParserConstants.RES_TABLE_TYPE_LIBRARY -> parseLibraryTypeChunk(chunkStart)

				ParserConstants.RES_TABLE_TYPE_OVERLAY -> parseOverlayTypeChunk(chunkStart)

				ParserConstants.RES_TABLE_TYPE_OVERLAY_POLICY ->
					throw IOException(
						String.format("Encountered unsupported chunk type RES_TABLE_TYPE_OVERLAY_POLICY at offset 0x%x ", chunkStart),
					)

				ParserConstants.RES_TABLE_TYPE_STAGED_ALIAS -> parseStagedAliasChunk(chunkStart)

				else -> LOG.warn("Unknown chunk type {} encountered at offset {}", type, chunkStart)
			}
		}
	}

	@Suppress("unused")
	@Throws(IOException::class)
	private fun parseTypeSpecChunk(chunkStart: Long) {
		input.checkInt16(0x0010, "Unexpected type spec header size")
		val chunkSize = input.readInt32()
		val expectedEndPos = chunkStart + chunkSize

		val id = input.readInt8()
		input.skip(3L)
		val entryCount = input.readInt32()
		for (i in 0 until entryCount) {
			val entryFlag = input.readInt32()
		}
		if (input.pos != expectedEndPos) {
			throw IOException(String.format("Error reading type spec chunk at offset 0x%x", chunkStart))
		}
	}

	@Throws(IOException::class)
	private fun parseLibraryTypeChunk(chunkStart: Long) {
		LOG.trace("parsing library type chunk starting at offset {}", chunkStart)
		input.checkInt16(12, "Unexpected header size")
		val chunkSize = input.readInt32()
		val expectedEndPos = chunkStart + chunkSize
		val count = input.readInt32()
		for (i in 0 until count) {
			val packageId = input.readInt32()
			val packageName = input.readString16Fixed(128)
			LOG.info("Found resource shared library {}, pkgId: {}", packageName, packageId)
			if (input.pos > expectedEndPos) {
				throw IOException("reading after chunk end")
			}
		}
		if (input.pos != expectedEndPos) {
			throw IOException(String.format("Error reading library chunk at offset 0x%x", chunkStart))
		}
	}

	/**
	 * Parse an <code>ResTable_type</code> (except for the 2 bytes <code>uint16_t</code>
	 * from <code>ResChunk_header</code>).
	 */
	@Throws(IOException::class)
	private fun parseTypeChunk(start: Long, pkg: PackageChunk) {
		/* int headerSize = */
		input.readInt16()
		/* int size = */
		val chunkSize = input.readUInt32()
		val chunkEnd = start + chunkSize
		input.mark(chunkSize.toInt())

		// The type identifier this chunk is holding. Type IDs start at 1 (corresponding
		// to the value of the type bits in a resource identifier). 0 is invalid.
		val typeId = input.readInt8()
		val typeName = checkNotNull(pkg.typeStrings).get(typeId - 1)

		val flags = input.readInt8()
		val isSparse = (flags and ParserConstants.FLAG_SPARSE) != 0
		val isOffset16 = (flags and ParserConstants.FLAG_OFFSET16) != 0

		input.readInt16() // ignore reserved value - should be zero but in some apps it is not zero; see #2402
		val entryCount = input.readInt32()
		val entriesStart = start + input.readInt32()

		val config = parseConfig()

		if (config.isInvalid) {
			LOG.warn("Invalid config flags detected: {}{}", typeName, config.qualifiers)
		}

		val offsets = ArrayList<EntryOffset>(entryCount)
		if (isSparse) {
			for (i in 0 until entryCount) {
				val idx = input.readInt16()
				val offset = input.readInt16() * 4 // The offset in ResTable_sparseTypeEntry::offset is stored divided by 4.
				offsets.add(EntryOffset(idx, offset))
			}
		} else if (isOffset16) {
			for (i in 0 until entryCount) {
				val offset = input.readInt16()
				if (offset != 0xFFFF) {
					offsets.add(EntryOffset(i, offset * 4))
				}
			}
		} else {
			for (i in 0 until entryCount) {
				offsets.add(EntryOffset(i, input.readInt32()))
			}
		}
		input.skipToPos(entriesStart, "Failed to skip to entries start")
		var ignoredEoc = 0 // ignored entries because they are located after end of chunk
		val processedIndices = HashSet<Int>(offsets.size * 2)
		for (entryOffset in offsets) {
			val offset = entryOffset.offset
			if (offset == ParserConstants.NO_ENTRY) {
				continue
			}
			val index = entryOffset.idx
			if (isSparse && !processedIndices.add(index)) {
				// Sometimes sparse type chunks contain multiple entries with the same index.
				// If we have processed the index once, we assume can ignore other entries with the same index.
				continue
			}
			val entryStartOffset = entriesStart + offset
			if (entryStartOffset >= chunkEnd) {
				// Certain resource obfuscated apps like com.facebook.orca have more entries defined
				// than actually fit into the chunk size -> ignore this entry
				ignoredEoc++
				// LOG.debug("Pos is after chunk end: {} end {}", entryStartOffset, chunkEnd);
				continue
			}
			if (entryStartOffset < input.pos) {
				// workaround for issue #2343: if the entryStartOffset is located before our current position
				input.reset()
			}
			input.skipToPos(entryStartOffset, "Expected start of entry " + index)
			parseEntry(pkg, typeId, index, config.qualifiers)
		}
		if (ignoredEoc > 0) {
			// invalid = data offset is after the chunk end
			LOG.warn("{} entries of type {} has been ignored (invalid offset)", ignoredEoc, typeName)
		}
		input.skipToPos(chunkEnd, "End of chunk")
	}

	private class EntryOffset(
		val idx: Int,
		val offset: Int,
	) {
		override fun toString(): String = StringJoiner(", ", EntryOffset::class.java.simpleName + "[", "]")
			.add("idx=$idx")
			.add("offset=$offset")
			.toString()
	}

	@Throws(IOException::class)
	private fun parseOverlayTypeChunk(chunkStart: Long) {
		LOG.trace("parsing overlay type chunk starting at offset {}", chunkStart)
		// read ResTable_overlayable_header
		/* headerSize = */
		input.readInt16() // usually 1032 bytes
		val chunkSize = input.readInt32() // e.g. 1056 bytes
		val expectedEndPos = chunkStart + chunkSize
		val name = input.readString16Fixed(256) // 512 bytes
		val actor = input.readString16Fixed(256) // 512 bytes
		LOG.trace("Overlay header data: name={} actor={}", name, actor)
		// skip: ResTable_overlayable_policy_header + ResTable_ref * x
		input.skipToPos(expectedEndPos, "overlay chunk end")
	}

	@Throws(IOException::class)
	private fun parseStagedAliasChunk(chunkStart: Long) {
		// read ResTable_staged_alias_header
		LOG.trace("parsing staged alias chunk starting at offset {}", chunkStart)
		/* headerSize = */
		input.readInt16()
		val chunkSize = input.readInt32()
		val expectedEndPos = chunkStart + chunkSize
		val count = input.readInt32()

		for (i in 0 until count) {
			// read ResTable_staged_alias_entry
			val stagedResId = input.readInt32()
			val finalizedResId = input.readInt32()
			LOG.debug("Staged alias: stagedResId {} finalizedResId {}", stagedResId, finalizedResId)
		}
		input.skipToPos(expectedEndPos, "staged alias chunk end")
	}

	@Throws(IOException::class)
	private fun parseEntry(pkg: PackageChunk, typeId: Int, entryId: Int, config: String) {
		val size = input.readInt16()
		val flags = input.readInt16()
		val isComplex = (flags and ParserConstants.FLAG_COMPLEX) != 0
		val isCompact = (flags and ParserConstants.FLAG_COMPACT) != 0

		val key = if (isCompact) size else input.readInt32()
		if (key == -1) {
			return
		}

		// resourceID as defined in AOSP make_resid()
		val resId = (pkg.id shl 24) or (typeId shl 16) or entryId
		val typeName = checkNotNull(pkg.typeStrings).get(typeId - 1)
		val origKeyName = checkNotNull(pkg.keyStrings).get(key)

		val newResEntry = buildResourceEntry(pkg, config, resId, typeName, origKeyName)
		if (isCompact) {
			val dataType = flags shr 8
			val data = input.readInt32()
			newResEntry.simpleValue = RawValue(dataType, data)
		} else if (isComplex || size == 16) {
			val parentRef = input.readInt32()
			val count = input.readInt32()
			newResEntry.parentRef = parentRef
			val values = ArrayList<RawNamedValue>(count)
			for (i in 0 until count) {
				values.add(parseValueMap())
			}
			newResEntry.namedValues = values
		} else {
			newResEntry.simpleValue = parseValue()
		}
	}

	private fun buildResourceEntry(pkg: PackageChunk, config: String, resId: Int, typeName: String, origKeyName: String): ResourceEntry {
		if (!root.getArgs().security.isValidEntryName(origKeyName)) {
			// malicious entry, ignore it
			// can't return null here, return stub without adding it to storage
			return STUB_ENTRY
		}

		var newResEntry: ResourceEntry
		if (useRawResName) {
			newResEntry = ResourceEntry(resId, pkg.name, typeName, origKeyName, config)
		} else {
			var resName = getResName(resId, origKeyName)
			newResEntry = ResourceEntry(resId, pkg.name, typeName, resName, config)
			val storage = checkNotNull(resStorage)
			val prevResEntry = storage.searchEntryWithSameName(newResEntry)
			if (prevResEntry != null) {
				if (prevResEntry.id == newResEntry.id) {
					// Check that every resource (identified by its resource ID) is only processed once.
					// We should not get resource entries with an identical id. This check is just for safety purposes,
					// otherwise Jadx can accumulate many GB of RAM as described in issue #2775 because resource names
					// are extended by every rename operation and are getting longer and longer...
					LOG.error("ResourceEntries with duplicate resource id found: {} {}", prevResEntry, newResEntry)
					resName = origKeyName // use the original name, not the renamed one
				}
				newResEntry = newResEntry.copyWithId(resName)

				// rename also previous entry for consistency
				val replaceForPrevEntry = prevResEntry.copyWithId(resName)
				LOG.trace("Resource name collision - renamed to {} and {}", newResEntry.keyName, replaceForPrevEntry.keyName)
				storage.replace(prevResEntry, replaceForPrevEntry)
				storage.addRename(replaceForPrevEntry)
			}
			if (origKeyName != newResEntry.keyName) {
				storage.addRename(newResEntry)
			}
		}

		checkNotNull(resStorage).add(newResEntry)
		return newResEntry
	}

	private fun getResName(resRef: Int, origKeyName: String): String {
		if (this.useRawResName) {
			return origKeyName
		}
		val storage = checkNotNull(resStorage)
		val renamedKey = storage.getRename(resRef)
		if (renamedKey != null) {
			return renamedKey
		}

		val fldRef: IFieldInfoRef? = root.getConstValues().getGlobalConstFields()[resRef]
		val constField = fldRef as? FieldNode

		val newResName = getNewResName(resRef, origKeyName, constField)
		if (origKeyName != newResName) {
			storage.addRename(resRef, newResName)
		}

		if (constField != null) {
			val newFieldName = ResNameUtils.convertToRFieldName(newResName)
			constField.rename(newFieldName)
			constField.add(AFlag.DONT_RENAME)
		}

		return newResName
	}

	private fun getNewResName(resRef: Int, origKeyName: String, constField: FieldNode?): String {
		var newResName: String
		if (constField == null || constField.getTopParentClass().isSynthetic()) {
			newResName = origKeyName
		} else {
			newResName = getBetterName(root.getArgs().resourceNameSource, origKeyName, constField.getName())
		}

		if (root.getArgs().isRenameValid) {
			val allowNonPrintable = !root.getArgs().isRenamePrintable
			newResName = ResNameUtils.sanitizeAsResourceName(newResName, String.format("_res_0x%08x", resRef), allowNonPrintable)
		}

		return newResName
	}

	@Throws(IOException::class)
	private fun parseValueMap(): RawNamedValue {
		val nameRef = input.readInt32()
		return RawNamedValue(nameRef, parseValue())
	}

	@Throws(IOException::class)
	private fun parseValue(): RawValue {
		input.checkInt16(8, "value size")
		input.checkInt8(0, "value res0 not 0")
		val dataType = input.readInt8()
		val data = input.readInt32()
		return RawValue(dataType, data)
	}

	@Throws(IOException::class)
	private fun parseConfig(): EntryConfig {
		val start = input.pos
		val size = input.readInt32()
		if (size < 4) {
			throw IOException("Config size < 4")
		}

		// Android zero fill this structure and only read the data present
		val configData = ByteArray(maxOf(52, size - 4))
		input.readFully(configData, 0, size - 4)
		val configIs = ParserStream(ByteArrayInputStream(configData))

		val mcc = configIs.readInt16().toShort()
		val mnc = configIs.readInt16().toShort()

		val language = unpackLocaleOrRegion(configIs.readInt8().toByte(), configIs.readInt8().toByte(), 'a')
		val country = unpackLocaleOrRegion(configIs.readInt8().toByte(), configIs.readInt8().toByte(), '0')

		val orientation = configIs.readInt8().toByte()
		val touchscreen = configIs.readInt8().toByte()
		val density = configIs.readInt16()

		val keyboard = configIs.readInt8().toByte()
		val navigation = configIs.readInt8().toByte()
		val inputFlags = configIs.readInt8().toByte()
		val grammaticalInflection = configIs.readInt8().toByte()

		val screenWidth = configIs.readInt16().toShort()
		val screenHeight = configIs.readInt16().toShort()

		val sdkVersion = configIs.readInt16().toShort()
		configIs.readInt16() // minorVersion must always be 0

		val screenLayout = configIs.readInt8().toByte()
		val uiMode = configIs.readInt8().toByte()
		val smallestScreenWidthDp = configIs.readInt16().toShort()
		val screenWidthDp = configIs.readInt16().toShort()
		val screenHeightDp = configIs.readInt16().toShort()

		val localeScript = readScriptOrVariantChar(4, configIs).toCharArray()
		val localeVariant = readScriptOrVariantChar(8, configIs).toCharArray()

		val screenLayout2 = configIs.readInt8().toByte()
		val colorMode = configIs.readInt8().toByte()
		configIs.readInt16() // reserved padding

		input.checkPos(start + size, "Config skip trailing bytes")

		return EntryConfig(
			mcc, mnc, language, country,
			orientation, touchscreen, density, keyboard, navigation,
			inputFlags, grammaticalInflection, screenWidth, screenHeight, sdkVersion,
			screenLayout, uiMode, smallestScreenWidthDp, screenWidthDp,
			screenHeightDp,
			if (localeScript.isEmpty()) null else localeScript,
			if (localeVariant.isEmpty()) null else localeVariant,
			screenLayout2,
			colorMode, false, size,
		)
	}

	private fun unpackLocaleOrRegion(in0: Byte, in1: Byte, base: Char): CharArray {
		// check high bit, if so we have a packed 3 letter code
		if (((in0.toInt() shr 7) and 1) == 1) {
			val first = in1.toInt() and 0x1F
			val second = ((in1.toInt() and 0xE0) shr 5) + ((in0.toInt() and 0x03) shl 3)
			val third = (in0.toInt() and 0x7C) shr 2

			// since this function handles languages & regions, we add the value(s) to the base char
			// which is usually 'a' or '0' depending on language or region.
			return charArrayOf((first + base.code).toChar(), (second + base.code).toChar(), (third + base.code).toChar())
		}
		return charArrayOf(in0.toInt().toChar(), in1.toInt().toChar())
	}

	@Throws(IOException::class)
	private fun readScriptOrVariantChar(length: Int): String = readScriptOrVariantChar(length, input)

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ResTableBinaryParser::class.java)

		private val STUB_ENTRY = ResourceEntry(-1, "stub", "stub", "stub", "")

		fun getBetterName(nameSource: ResourceNameSource, resName: String, codeName: String): String = when (nameSource) {
			ResourceNameSource.AUTO -> BetterName.getBetterResourceName(resName, codeName)
			ResourceNameSource.RESOURCES -> resName
			ResourceNameSource.CODE -> codeName
			else -> throw JadxRuntimeException("Unexpected ResourceNameSource value: $nameSource")
		}

		@Throws(IOException::class)
		private fun readScriptOrVariantChar(length: Int, ps: ParserStream): String {
			val start = ps.pos
			val sb = StringBuilder(16)
			for (i in 0 until length) {
				val ch = ps.readInt8().toShort()
				if (ch.toInt() == 0) {
					break
				}
				sb.append(ch.toInt().toChar())
			}
			ps.skipToPos(start + length, "readScriptOrVariantChar")
			return sb.toString()
		}
	}
}
