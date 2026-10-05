package kadx.plugins.input.aab.parsers

import com.android.aapt.Resources.XmlAttribute
import com.android.aapt.Resources.XmlElement
import com.android.aapt.Resources.XmlNamespace
import com.android.aapt.Resources.XmlNode
import kadx.api.ICodeInfo
import kadx.api.ICodeWriter
import kadx.core.dex.nodes.RootNode
import kadx.core.utils.StringUtils
import kadx.core.utils.android.AndroidResourcesMap
import kadx.core.xmlgen.XMLChar
import kadx.core.xmlgen.XmlDeobf
import kadx.core.xmlgen.XmlGenUtils
import java.io.InputStream
import java.util.Random

/**
 * AAB 内 XML（manifest/资源 xml）的 protobuf 解析器。
 *
 * **背景**：把 XmlNode/XmlElement 递归渲染成 XML 文本（[writer]），处理命名空间映射、
 * 属性名反混淆（deobfClassName）、非法标签名的随机替换，以及 package 属性的记录。
 */
public class ResXmlProtoParser(private val rootNode: RootNode) : CommonProtoParser() {

	private var nsMap: MutableMap<String, String>? = null
	private val tagAttrDeobfNames = HashMap<String, String>()

	private lateinit var writer: ICodeWriter

	private var currentTag: String? = null
	private var appPackageName: String? = null
	private val isPrettyPrint = !rootNode.getArgs().isSkipXmlPrettyPrint

	@Synchronized
	public fun parse(inputStream: InputStream): ICodeInfo {
		nsMap = HashMap()
		writer = rootNode.makeCodeWriter()
		writer.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
		decode(decodeProto(inputStream))
		nsMap = null
		return writer.finish()
	}

	private fun decode(n: XmlNode) {
		if (n.hasSource()) {
			writer.attachSourceLine(n.source.lineNumber)
		}
		writer.add(StringUtils.escapeXML(n.text.trim()))
		if (n.hasElement()) {
			decode(n.element)
		}
	}

	private fun decode(e: XmlElement) {
		var tag = deobfClassName(e.name)
		tag = getValidTagAttributeName(tag ?: throw NullPointerException("tag is null")) // 原 Java：null 时后续 NPE，保持一致
		currentTag = tag
		writer.startLine('<').add(tag)

		decodeNamespaces(e)
		decodeAttributes(e)

		if (e.childCount > 0) {
			writer.add('>')
			writer.incIndent()
			for (i in 0 until e.childCount) {
				val oldNsMap = HashMap(checkNotNull(nsMap))
				decode(e.getChild(i))
				nsMap = oldNsMap
			}
			writer.decIndent()
			writer.startLine("</").add(tag).add('>')
		} else {
			writer.add(" />")
		}
	}

	private fun decodeNamespaces(e: XmlElement) {
		val nsCount = e.namespaceDeclarationCount
		val newLine = nsCount != 1 && isPrettyPrint
		if (nsCount > 0) {
			writer.add(' ')
		}
		for (i in 0 until nsCount) {
			decodeNamespace(e.getNamespaceDeclaration(i), newLine, i == nsCount - 1)
		}
	}

	private fun decodeNamespace(n: XmlNamespace, newLine: Boolean, isLastElement: Boolean) {
		val prefix = n.prefix
		val uri = n.uri
		checkNotNull(nsMap)[uri] = prefix
		writer.add("xmlns:").add(prefix).add("=\"").add(uri).add('"')
		if (isLastElement) {
			return
		}
		if (newLine) {
			writer.startLine().addIndent()
		} else {
			writer.add(' ')
		}
	}

	private fun decodeAttributes(e: XmlElement) {
		val attrsCount = e.attributeCount
		val newLine = attrsCount != 1 && isPrettyPrint
		if (attrsCount > 0) {
			writer.add(' ')
			if (isPrettyPrint) {
				writer.startLine().addIndent()
			}
		}
		val attrCache = HashSet<String>()
		for (i in 0 until attrsCount) {
			decodeAttribute(e.getAttribute(i), attrCache, newLine, i == attrsCount - 1)
		}
	}

	private fun decodeAttribute(a: XmlAttribute, attrCache: MutableSet<String>, newLine: Boolean, isLastElement: Boolean) {
		val name = getAttributeFullName(a)
		if (XmlDeobf.isDuplicatedAttr(name, attrCache)) {
			return
		}
		val value = deobfClassName(getAttributeValue(a))
		writer.add(name).add("=\"").add(StringUtils.escapeXML(checkNotNull(value))).add('"')
		memorizePackageName(name, value)
		if (isLastElement) {
			return
		}
		if (newLine) {
			writer.startLine().addIndent()
		} else {
			writer.add(' ')
		}
	}

	private fun getAttributeFullName(a: XmlAttribute): String {
		val namespaceUri = a.namespaceUri
		var namespace: String? = null
		if (namespaceUri.isNotEmpty()) {
			namespace = nsMap?.get(namespaceUri)
		}

		var attrName = a.name
		if (attrName.isEmpty()) {
			// 某些优化工具会清空名字，因为 Android 平台不需要它
			val resId = a.resourceId
			val str = AndroidResourcesMap.getResName(resId)
			if (str != null) {
				namespace = nsMap?.get(ANDROID_NS_URL)
				// 截掉 / 之前的类型部分
				val typeEnd = str.indexOf('/')
				if (typeEnd != -1) {
					attrName = str.substring(typeEnd + 1)
				} else {
					attrName = str
				}
			} else {
				attrName = "_unknown_"
			}
		}

		return if (namespace != null) namespace + ":" + attrName else attrName
	}

	private fun getAttributeValue(a: XmlAttribute): String? {
		if (!a.value.isEmpty()) {
			return a.value
		}
		return parse(a.compiledItem)
	}

	private fun memorizePackageName(attrName: String, attrValue: String?) {
		if ("manifest" == currentTag && "package" == attrName) {
			appPackageName = attrValue
		}
	}

	private fun deobfClassName(className: String?): String? {
		val newName = XmlDeobf.deobfClassName(rootNode, checkNotNull(className), appPackageName)
		if (newName != null) {
			return newName
		}
		return className
	}

	private fun getValidTagAttributeName(originalName: String): String {
		if (XMLChar.isValidName(originalName)) {
			return originalName
		}
		val cached = tagAttrDeobfNames[originalName]
		if (cached != null) {
			return cached
		}
		var generated: String
		do {
			generated = generateTagAttrName()
		} while (tagAttrDeobfNames.containsValue(generated))
		tagAttrDeobfNames[originalName] = generated
		return generated
	}

	private fun decodeProto(inputStream: InputStream): XmlNode = XmlNode.parseFrom(XmlGenUtils.readData(inputStream))

	private companion object {
		private fun generateTagAttrName(): String {
			val length = 6
			val r = Random()
			val sb = StringBuilder()
			for (i in 1..length) {
				sb.append(((r.nextInt(26) + 'a'.code)).toChar())
			}
			return sb.toString()
		}
	}
}
