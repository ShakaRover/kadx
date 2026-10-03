package jadx.core.xmlgen

import jadx.api.security.IJadxSecurity
import jadx.core.utils.exceptions.JadxRuntimeException
import jadx.core.xmlgen.entry.ValuesParser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashMap

// TODO: move to Android specific module!

/**
 * 加载并保存 Android Manifest 属性规范（`attrs.xml` / `attrs_manifest.xml`）。
 *
 * 解析系统属性（`android:` 前缀）与 APK 自身属性，用于把清单/资源里的整型值
 * 解码成可读的枚举/标志名称（例如 `orientation` 的 `0/1/2` -> `portrait/landscape`）。
 */
class ManifestAttributes(private val security: IJadxSecurity) {

	private enum class MAttrType {
		ENUM,
		FLAG,
	}

	private class MAttr(val type: MAttrType) {
		val values: MutableMap<Long, String> = LinkedHashMap()

		fun addValue(key: Long, value: String) {
			values[key] = value
		}

		override fun toString(): String = "[" + type + ", " + values + ']'
	}

	/**
	 * Map containing default Android resource attribute definitions.
	 * Keys are Android attribute names (e.g., "android:layout_width"),
	 * and values are their corresponding [MAttr] objects.
	 */
	private val attrMap: MutableMap<String, MAttr> = HashMap()

	private val appAttrMap: MutableMap<String, MAttr> = HashMap()

	init {
		parseAll()
	}

	private fun parseAll() {
		parse(loadXML(ATTR_XML))
		parse(loadXML(MANIFEST_ATTR_XML))
		LOG.debug("Loaded android attributes count: {}", attrMap.size)
	}

	private fun loadXML(xml: String): Document {
		val xmlStream = ManifestAttributes::class.java.getResourceAsStream(xml)
		try {
			if (xmlStream == null) {
				throw JadxRuntimeException("$xml not found in classpath")
			}
			return security.parseXml(xmlStream)
		} catch (e: Exception) {
			throw JadxRuntimeException("Xml load error, file: $xml", e)
		} finally {
			xmlStream?.close()
		}
	}

	private fun parse(doc: Document) {
		val nodeList = doc.childNodes
		for (count in 0 until nodeList.length) {
			val node = nodeList.item(count)
			if (node.nodeType == Node.ELEMENT_NODE &&
				node.hasChildNodes()
			) {
				parseAttrList(node.childNodes)
			}
		}
	}

	private fun parseAttrList(nodeList: NodeList) {
		for (count in 0 until nodeList.length) {
			val tempNode = nodeList.item(count)
			if (tempNode.nodeType == Node.ELEMENT_NODE &&
				tempNode.hasAttributes() &&
				tempNode.hasChildNodes()
			) {
				var name: String? = null
				val nodeMap = tempNode.attributes
				for (i in 0 until nodeMap.length) {
					val node = nodeMap.item(i)
					if (node.nodeName == "name") {
						name = node.nodeValue
						break
					}
				}
				if (name != null && tempNode.nodeName == "attr") {
					parseValues(name, tempNode.childNodes)
				} else {
					parseAttrList(tempNode.childNodes)
				}
			}
		}
	}

	private fun parseValues(name: String, nodeList: NodeList) {
		var attr: MAttr? = null
		for (count in 0 until nodeList.length) {
			val tempNode = nodeList.item(count)
			if (tempNode.nodeType == Node.ELEMENT_NODE &&
				tempNode.hasAttributes()
			) {
				if (attr == null) {
					if (tempNode.nodeName == "enum") {
						attr = MAttr(MAttrType.ENUM)
					} else if (tempNode.nodeName == "flag") {
						attr = MAttr(MAttrType.FLAG)
					}
					if (attr == null) {
						return
					}
					attrMap["android:$name"] = attr
				}
				val attributes = tempNode.attributes
				val nameNode = attributes.getNamedItem("name")
				if (nameNode != null) {
					val valueNode = attributes.getNamedItem("value")
					if (valueNode != null) {
						try {
							val key: Long
							var nodeValue = valueNode.nodeValue
							if (nodeValue.startsWith("0x")) {
								nodeValue = nodeValue.substring(2)
								key = nodeValue.toLong(16)
							} else {
								key = nodeValue.toLong()
							}
							checkNotNull(attr).addValue(key, nameNode.nodeValue)
						} catch (e: NumberFormatException) {
							LOG.debug("Failed parse manifest number", e)
						}
					}
				}
			}
		}
	}

	/**
	 * 把属性名与整型值解码为可读名称。
	 * ENUM 直接查表；FLAG 按位拆分为多个名称（用 `|` 连接）。
	 */
	fun decode(attrNameRaw: String, valueRaw: Long): String? {
		var attrName = attrNameRaw
		var attr = attrMap[attrName]
		if (attr == null) {
			if (attrName.contains(":")) {
				attrName = attrName.split(":", limit = 2)[1]
			}
			attr = appAttrMap[attrName]
			if (attr == null) {
				return null
			}
		}

		val attrValuesMap = attr.values
		if (attr.type == MAttrType.ENUM) {
			return attrValuesMap[valueRaw]
		} else if (attr.type == MAttrType.FLAG) {
			val flagList = ArrayList<String>()
			val attrKeys = ArrayList(attrValuesMap.keys)
			attrKeys.sortDescending() // sort descending
			var value = valueRaw
			for (key in attrKeys) {
				val attrValue = checkNotNull(attrValuesMap[key])
				if (value == key) {
					flagList.add(attrValue)
					break
				} else if ((key != 0L) && ((value and key) == key)) {
					flagList.add(attrValue)
					value = value xor key
				}
			}
			return flagList.joinToString("|")
		}
		return null
	}

	/** 从已解析的资源表中收集 APK 自身的 `attr` 枚举/标志定义。 */
	fun updateAttributes(parser: IResTableParser) {
		appAttrMap.clear()

		val resStorage = checkNotNull(parser.resStorage)
		val vp = ValuesParser(parser.strings, resStorage.resourcesNames)

		for (ri in resStorage.resources) {
			if (ri.protoValue != null) {
				// Aapt proto decoder resolves attributes by itself.
				continue
			}

			val namedValues = ri.namedValues
			if (ri.typeName == "attr" && namedValues != null && namedValues.size > 1) {
				val first = namedValues[0]
				val attrTyp: MAttrType
				val attrTypeVal = first.rawValue.data and 0xff0000
				if (attrTypeVal == ParserConstants.ATTR_TYPE_FLAGS) {
					attrTyp = MAttrType.FLAG
				} else if (attrTypeVal == ParserConstants.ATTR_TYPE_ENUM) {
					attrTyp = MAttrType.ENUM
				} else {
					continue
				}
				val attr = MAttr(attrTyp)
				for (i in 1 until namedValues.size) {
					val rv = namedValues[i]
					val value = checkNotNull(vp.decodeNameRef(rv.nameRef))
					attr.addValue(rv.rawValue.data.toLong(), if (value.startsWith("id.")) value.substring(3) else value)
				}
				appAttrMap[ri.keyName] = attr
			}
		}
	}

	companion object {
		private val LOG: Logger = LoggerFactory.getLogger(ManifestAttributes::class.java)

		private const val ATTR_XML = "/android/attrs.xml"
		private const val MANIFEST_ATTR_XML = "/android/attrs_manifest.xml"
	}
}
