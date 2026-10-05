package kadx.core.xmlgen

import kadx.api.ICodeInfo
import kadx.api.ICodeWriter
import kadx.api.ResourcesLoader
import kadx.core.dex.info.ConstStorage
import kadx.core.dex.nodes.ClassNode
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.StringUtils
import kadx.core.utils.android.AndroidResourcesMap
import kadx.core.utils.exceptions.KadxRuntimeException
import kadx.core.xmlgen.entry.ValuesParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.util.HashMap
import java.util.HashSet
import java.util.Random

/**
 * 二进制 XML（AndroidManifest.xml、layout 等）解析器。
 *
 * 解析 chunk 结构（字符串池、资源映射、命名空间、元素、属性、CDATA），
 * 并借助 [ManifestAttributes] 把属性值解码成可读文本，同时做类名反混淆。
 *
 * **Kotlin 转换说明**：
 * - 原 `synchronized` 方法改为 `@Synchronized`；
 * - 基类字段 `is` 重命名为 [input]（`is` 是 Kotlin 关键字）；
 * - 每次 [parse] 结束后释放命名空间缓存，避免解析器复用时内存滞留。
 */
class BinaryXMLParser(private val rootNode: RootNode) : CommonBinaryParser() {

	private val manifestAttributes: ManifestAttributes = rootNode.initManifestAttributes()
	private val attrNewLine: Boolean = !rootNode.getArgs().isSkipXmlPrettyPrint

	private val resNames: Map<Int, String>
	private var nsMap: MutableMap<String, String> = HashMap()
	private var nsMapGenerated: MutableSet<String> = HashSet()
	private var definedNamespaces: MutableSet<String> = HashSet()
	private val tagAttrDeobfNames: MutableMap<String, String> = HashMap()

	private lateinit var writer: ICodeWriter
	private var strings: BinaryXMLStrings? = null
	private var currentTag = "ERROR"
	private var firstElement = false
	private var valuesParser: ValuesParser? = null
	private var isLastEnd = true
	private var isOneLine = true
	private var namespaceDepth = 0
	private var resourceIds: IntArray? = null
	private var appPackageName: String? = null

	private var classNameCache: Map<String, ClassNode>? = null

	init {
		try {
			val constStorage: ConstStorage = rootNode.getConstValues()
			resNames = constStorage.resourcesNames
		} catch (e: Exception) {
			throw KadxRuntimeException("BinaryXMLParser init error", e)
		}
	}

	@Synchronized
	@Throws(IOException::class)
	fun parse(inputStream: InputStream): ICodeInfo {
		resourceIds = null
		input = ParserStream(inputStream)
		if (!isBinaryXml()) {
			return ResourcesLoader.loadToCodeWriter(input)
		}
		nsMapGenerated = HashSet()
		nsMap = HashMap()
		definedNamespaces = HashSet()
		writer = rootNode.makeCodeWriter()
		writer.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
		firstElement = true
		decode()
		nsMap = HashMap()
		definedNamespaces = HashSet()
		val codeInfo = writer.finish()
		this.classNameCache = null // reset class name cache
		return codeInfo
	}

	private fun isBinaryXml(): Boolean {
		input.mark(4)
		input.readInt16() // version
		val h = input.readInt16() // header size
		// Some APK Manifest.xml the version is 0
		if (h == 0x0008) {
			return true
		}
		input.reset()
		return false
	}

	@Throws(IOException::class)
	private fun decode() {
		val size = input.readInt32().toLong()
		while (input.pos < size) {
			val type = input.readInt16()
			when (type) {
				ParserConstants.RES_NULL_TYPE -> {
					// NullType is just doing nothing
				}

				ParserConstants.RES_STRING_POOL_TYPE -> {
					strings = parseStringPoolNoType()
					valuesParser = ValuesParser(strings, resNames)
				}

				ParserConstants.RES_XML_RESOURCE_MAP_TYPE -> parseResourceMap()

				ParserConstants.RES_XML_START_NAMESPACE_TYPE -> parseNameSpace()

				ParserConstants.RES_XML_CDATA_TYPE -> parseCData()

				ParserConstants.RES_XML_END_NAMESPACE_TYPE -> parseNameSpaceEnd()

				ParserConstants.RES_XML_START_ELEMENT_TYPE -> parseElement()

				ParserConstants.RES_XML_END_ELEMENT_TYPE -> parseElementEnd()

				else -> {
					if (namespaceDepth == 0) {
						// skip padding on file end
						return
					}
					die("Type: 0x" + Integer.toHexString(type) + " not yet implemented")
				}
			}
		}
	}

	@Throws(IOException::class)
	private fun parseResourceMap() {
		if (input.readInt16() != 0x8) {
			die("Header size of resmap is not 8!")
		}
		val size = input.readInt32()
		val len = (size - 8) / 4
		val ids = IntArray(len)
		resourceIds = ids
		for (i in 0 until len) {
			ids[i] = input.readInt32()
		}
	}

	@Throws(IOException::class)
	private fun parseNameSpace() {
		val headerSize = input.readInt16()
		if (headerSize > 0x10) {
			LOG.warn("Invalid namespace header")
		} else if (headerSize < 0x10) {
			die("NAMESPACE header is not 0x10 big")
		}
		val size = input.readInt32()
		if (size > 0x18) {
			LOG.warn("Invalid namespace size")
		} else if (size < 0x18) {
			die("NAMESPACE header chunk is not 0x18 big")
		}

		val beginLineNumber = input.readInt32()
		val comment = input.readInt32()
		val beginPrefix = input.readInt32()
		val beginURI = input.readInt32()
		input.skip((headerSize - 0x10).toLong())

		val nsKey = getString(beginURI)
		val nsValue = getString(beginPrefix)
		if (StringUtils.notBlank(nsKey) && !nsMap.containsValue(nsValue)) {
			nsMap.putIfAbsent(nsKey, nsValue)
		}
		namespaceDepth++
	}

	@Throws(IOException::class)
	private fun parseNameSpaceEnd() {
		val headerSize = input.readInt16()
		if (headerSize > 0x10) {
			LOG.warn("Invalid namespace end")
		} else if (headerSize < 0x10) {
			die("NAMESPACE end is not 0x10 big")
		}
		val dataSize = input.readInt32()
		if (dataSize != 0x18) {
			LOG.warn("Invalid namespace end size")
		}
		val endLineNumber = input.readInt32()
		val comment = input.readInt32()
		val endPrefix = input.readInt32()
		val endURI = input.readInt32()
		input.skip((headerSize - 0x10).toLong())
		namespaceDepth--

		val nsKey = getString(endURI)
		val nsValue = getString(endPrefix)
		if (StringUtils.notBlank(nsKey) && !nsMap.containsValue(nsValue)) {
			nsMap.putIfAbsent(nsKey, nsValue)
		}
	}

	@Throws(IOException::class)
	private fun parseCData() {
		if (input.readInt16() != 0x10) {
			die("CDATA header is not 0x10")
		}
		if (input.readInt32() != 0x1C) {
			die("CDATA header chunk is not 0x1C")
		}
		val lineNumber = input.readInt32()
		input.skip(4L)

		val strIndex = input.readInt32()
		val str = getString(strIndex)
		if (!isLastEnd) {
			isLastEnd = true
			writer.add('>')
		}
		writer.attachSourceLine(lineNumber)
		val escapedStr = StringUtils.escapeXML(str)
		writer.add(escapedStr)

		val size = input.readInt16()
		input.skip((size - 2).toLong())
	}

	@Throws(IOException::class)
	private fun parseElement() {
		if (firstElement) {
			firstElement = false
		} else {
			writer.incIndent()
		}
		if (input.readInt16() != 0x10) {
			die("ELEMENT HEADER SIZE is not 0x10")
		}
		// TODO: Check element chunk size
		val startPos = input.pos
		val elementSize = input.readInt32()
		val elementBegLineNumber = input.readInt32()
		val comment = input.readInt32()
		val startNS = input.readInt32()
		val startNSName = input.readInt32() // actually is elementName...
		if (!isLastEnd && currentTag != "ERROR") {
			writer.add('>')
		}
		isOneLine = true
		isLastEnd = false
		currentTag = deobfClassName(getString(startNSName))
		currentTag = getValidTagAttributeName(currentTag)
		writer.startLine('<').add(currentTag)
		writer.attachSourceLine(elementBegLineNumber)
		val attributeStart = input.readInt16()
		if (attributeStart != 0x14) {
			die("startNS's attributeStart is not 0x14")
		}
		val attributeSize = input.readInt16()
		if (attributeSize < 0x14) {
			die("startNS's attributeSize is less than 0x14")
		}

		val attributeCount = input.readInt16()
		val idIndex = input.readInt16()
		val classIndex = input.readInt16()
		val styleIndex = input.readInt16()
		if (currentTag == "manifest" || definedNamespaces.size != nsMap.size) {
			for ((key, value) in nsMap) {
				if (!definedNamespaces.contains(key)) {
					definedNamespaces.add(key)
					val nsValue = getValidTagAttributeName(value)
					writer.add(" xmlns")
					if (nsValue.trim { it <= ' ' }.isNotEmpty()) {
						writer.add(':')
						writer.add(nsValue)
					}
					writer.add("=\"").add(StringUtils.escapeXML(key)).add('"')
				}
			}
		}
		val attrCache = HashSet<String>()
		val attrNewLine = attributeCount != 1 && this.attrNewLine
		for (i in 0 until attributeCount) {
			parseAttribute(i, attrNewLine, attrCache, attributeSize)
		}
		val endPos = input.pos
		if (endPos - startPos + 0x4 < elementSize) {
			input.skip(elementSize - (endPos - startPos + 0x4))
		}
	}

	@Throws(IOException::class)
	private fun parseAttribute(i: Int, newLine: Boolean, attrCache: MutableSet<String>, attributeSize: Int) {
		val attributeNS = input.readInt32()
		val attributeName = input.readInt32()
		val attributeRawValue = input.readInt32()
		input.skip(3L)
		val attrValDataType = input.readInt8()
		val attrValData = input.readInt32()

		input.skip((attributeSize - 0x14).toLong())

		var shortNsName: String? = null
		if (attributeNS != -1) {
			shortNsName = getAttributeNS(attributeNS, newLine)
		}
		val attrName = getValidTagAttributeName(getAttributeName(attributeName))
		val attrFullName = if (shortNsName != null) "$shortNsName:$attrName" else attrName
		// do not dump duplicated values
		if (XmlDeobf.isDuplicatedAttr(attrFullName, attrCache)) {
			return
		}

		if (newLine) {
			writer.startLine().addIndent()
		} else {
			writer.add(' ')
		}
		writer.add(attrFullName).add("=\"")
		var decodedAttr = manifestAttributes.decode(attrFullName, attrValData.toLong())
		if (decodedAttr != null) {
			memorizePackageName(attrName, decodedAttr)
			if (isDeobfCandidateAttr(attrFullName)) {
				decodedAttr = deobfClassName(decodedAttr)
			}
			attachClassNode(writer, attrName, decodedAttr)
			writer.add(StringUtils.escapeXML(decodedAttr))
		} else {
			decodeAttribute(attributeNS, attrValDataType, attrValData, attrFullName)
		}
		if (shortNsName != null && shortNsName == "android") {
			if (attrName == "pathData") {
				rootNode.gradleInfoStorage.isVectorPathData = true
			} else if (attrName == "fillType") {
				rootNode.gradleInfoStorage.isVectorFillType = true
			}
		}
		writer.add('"')
	}

	private fun getAttributeNS(attributeNS: Int, newLine: Boolean): String? {
		var attrUrl = getString(attributeNS)
		if (attrUrl.isEmpty()) {
			if (ParserConstants.isResInternalId(attributeNS)) {
				return null
			} else {
				attrUrl = ParserConstants.ANDROID_NS_URL
			}
		}
		var attrName = nsMap[attrUrl]
		if (attrName == null) {
			attrName = generateNameForNS(attrUrl, newLine)
		}
		return attrName
	}

	private fun generateNameForNS(attrUrl: String, newLine: Boolean): String {
		var attrName: String
		if (ParserConstants.ANDROID_NS_URL == attrUrl) {
			attrName = ParserConstants.ANDROID_NS_VALUE
			nsMap[ParserConstants.ANDROID_NS_URL] = attrName
		} else {
			var generated: String
			var i = 1
			while (true) {
				generated = "ns$i"
				if (!nsMapGenerated.contains(generated) && !nsMap.containsValue(generated)) {
					nsMapGenerated.add(generated)
					// do not add generated value to nsMap
					// because attrUrl might be used in a neighbor element, but never defined
					break
				}
				i++
			}
			attrName = generated
		}
		if (newLine) {
			writer.startLine().addIndent()
		} else {
			writer.add(' ')
		}
		writer.add("xmlns:").add(attrName).add("=\"").add(attrUrl).add("\" ")
		return attrName
	}

	private fun getAttributeName(id: Int): String {
		// As the outcome of https://github.com/skylot/jadx/issues/1208
		// Android seems to favor entries from AndroidResMap and only if
		// there is no entry uses the values form the XML string pool
		val ids = resourceIds
		if (ids != null && 0 <= id && id < ids.size) {
			val resId = ids[id]
			val str = AndroidResourcesMap.getResName(resId)
			if (str != null) {
				// cut type before /
				val typeEnd = str.indexOf('/')
				if (typeEnd != -1) {
					return str.substring(typeEnd + 1)
				}
				return str
			}
		}

		val str = getString(id)
		if (str.isEmpty()) {
			return "NOT_FOUND_0x" + Integer.toHexString(id)
		}
		return str
	}

	private fun getString(strId: Int): String {
		val strings = checkNotNull(this.strings)
		if (0 <= strId && strId < strings.size()) {
			return strings.get(strId)
		}
		return "NOT_FOUND_STR_0x" + Integer.toHexString(strId)
	}

	private fun decodeAttribute(attributeNS: Int, attrValDataType: Int, attrValData: Int, attrFullName: String) {
		if (attrValDataType == ParserConstants.TYPE_REFERENCE) {
			// reference custom processing
			val resName = resNames[attrValData]
			if (resName != null) {
				writer.add('@')
				if (resName.startsWith("id/")) {
					writer.add('+')
				}
				writer.add(resName)
			} else {
				val androidResName = AndroidResourcesMap.getResName(attrValData)
				if (androidResName != null) {
					writer.add("@android:").add(androidResName)
				} else if (attrValData == 0) {
					writer.add("@null")
				} else {
					writer.add("0x").add(Integer.toHexString(attrValData))
				}
			}
		} else {
			var str: String?
			try {
				str = checkNotNull(valuesParser).decodeValue(attrValDataType, attrValData)
			} catch (e: KadxRuntimeException) {
				LOG.error("Failed to decode attribute value of \"{}\"", attrFullName, e)
				str = null
			}
			memorizePackageName(attrFullName, str)
			if (isDeobfCandidateAttr(attrFullName)) {
				str = deobfClassName(checkNotNull(str))
			}
			attachClassNode(writer, attrFullName, str)
			writer.add(if (str != null) StringUtils.escapeXML(str) else "null")
		}
	}

	@Throws(IOException::class)
	private fun parseElementEnd() {
		if (input.readInt16() != 0x10) {
			die("ELEMENT END header is not 0x10")
		}
		if (input.readInt32() != 0x18) {
			die("ELEMENT END header chunk is not 0x18 big")
		}
		val endLineNumber = input.readInt32()
		val comment = input.readInt32()
		val elementNS = input.readInt32()
		val elementNameId = input.readInt32()
		var elemName = deobfClassName(getString(elementNameId))
		elemName = getValidTagAttributeName(elemName)
		if (currentTag == elemName && isOneLine && !isLastEnd) {
			writer.add("/>")
		} else {
			writer.startLine("</")
			writer.attachSourceLine(endLineNumber)
			// if (elementNS != -1) {
			// writer.add(getString(elementNS)).add(':');
			// }
			writer.add(elemName).add('>')
		}
		isLastEnd = true
		if (writer.getIndent() != 0) {
			writer.decIndent()
		}
	}

	private fun getValidTagAttributeName(originalName: String): String {
		if (XMLChar.isValidName(originalName)) {
			return originalName
		}
		val existing = tagAttrDeobfNames[originalName]
		if (existing != null) {
			return existing
		}
		var generated: String
		do {
			generated = generateTagAttrName()
		} while (tagAttrDeobfNames.containsValue(generated))
		tagAttrDeobfNames[originalName] = generated
		return generated
	}

	private fun attachClassNode(writer: ICodeWriter, attrFullName: String, clsName: String?) {
		if (!writer.isMetadataSupported()) {
			return
		}
		if (clsName == null || attrFullName != "android:name") {
			return
		}
		val clsFullName: String
		if (clsName.startsWith(".")) {
			clsFullName = appPackageName + clsName
		} else {
			clsFullName = clsName
		}
		val cache = classNameCache ?: rootNode.buildFullAliasClassCache().also { classNameCache = it }
		val classNode = cache[clsFullName]
		if (classNode != null) {
			writer.attachAnnotation(classNode)
		}
	}

	private fun deobfClassName(className: String): String {
		val newName = XmlDeobf.deobfClassName(rootNode, className, appPackageName)
		return newName ?: className
	}

	private fun isDeobfCandidateAttr(attrFullName: String): Boolean = "android:name" == attrFullName

	private fun memorizePackageName(attrFullName: String, attrValue: String?) {
		if ("manifest" == currentTag && "package" == attrFullName) {
			appPackageName = attrValue
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(BinaryXMLParser::class.java)

		private fun generateTagAttrName(): String {
			val length = 6
			val r = Random()
			val sb = StringBuilder()
			for (i in 1..length) {
				sb.append((r.nextInt(26) + 'a'.code).toChar())
			}
			return sb.toString()
		}
	}
}
