package jadx.core.xmlgen

import jadx.api.ICodeInfo
import jadx.api.ICodeWriter
import jadx.api.JadxArgs
import jadx.api.impl.SimpleCodeWriter
import jadx.core.utils.StringUtils
import jadx.core.xmlgen.entry.ProtoValue
import jadx.core.xmlgen.entry.RawNamedValue
import jadx.core.xmlgen.entry.ResourceEntry
import jadx.core.xmlgen.entry.ValuesParser
import java.util.ArrayList
import java.util.HashMap

/**
 * 把资源存储 [ResourceStorage] 渲染为 `res/values/` 下的 XML 文件。
 *
 * 支持两种来源：
 * - 二进制 `.arsc` 的原始值（[RawNamedValue]）；
 * - AAB 的 protobuf 值（[ProtoValue]）。
 *
 * 生成时按“配置 + 类型”分文件，并对清单属性值做枚举/标志解码。
 */
class ResXmlGen(
	private val resStorage: ResourceStorage,
	private val vp: ValuesParser,
	private val manifestAttributes: ManifestAttributes,
) {

	fun makeResourcesXml(args: JadxArgs): List<ResContainer> {
		val contMap = HashMap<String, ICodeWriter>()
		for (ri in resStorage.resources) {
			if (SKIP_RES_TYPES.contains(ri.typeName)) {
				continue
			}
			val fn = getFileName(ri)
			val cw = contMap[fn] ?: SimpleCodeWriter(args).also {
				it.add("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
				it.startLine("<resources>")
				it.incIndent()
				contMap[fn] = it
			}
			addValue(cw, ri)
		}

		val files = ArrayList<ResContainer>(contMap.size)
		for ((fileName, content) in contMap) {
			content.decIndent()
			content.startLine("</resources>")
			val codeInfo = content.finish()
			files.add(ResContainer.textResource(fileName, codeInfo))
		}
		files.sort()
		return files
	}

	private fun addValue(cw: ICodeWriter, ri: ResourceEntry) {
		val protoValue = ri.protoValue
		val simpleValue = ri.simpleValue
		if (protoValue != null) {
			if (protoValue.value != null && protoValue.namedValues == null) {
				addSimpleValue(cw, ri.typeName, ri.typeName, "name", ri.keyName, protoValue.value)
			} else {
				cw.startLine()
				cw.add('<').add(ri.typeName).add(' ')
				val itemTag = "item"
				cw.add("name=\"").add(ri.keyName).add('"')
				if (ri.typeName == "attr" && protoValue.value != null) {
					cw.add(" format=\"").add(protoValue.value).add('"')
				}
				if (protoValue.parent != null) {
					cw.add(" parent=\"").add(protoValue.parent).add('"')
				}
				cw.add(">")

				cw.incIndent()
				for (value in checkNotNull(protoValue.namedValues)) {
					addProtoItem(cw, itemTag, ri.typeName, value)
				}
				cw.decIndent()
				cw.startLine().add("</").add(ri.typeName).add('>')
			}
		} else if (simpleValue != null) {
			val valueStr = vp.decodeValue(simpleValue)
			addSimpleValue(cw, ri.typeName, ri.typeName, "name", ri.keyName, valueStr)
		} else {
			val namedValues = checkNotNull(ri.namedValues)
			var skipNamedValues = false
			cw.startLine()
			cw.add('<').add(ri.typeName).add(" name=\"")
			var itemTag = "item"
			if (ri.typeName == "attr" && namedValues.isNotEmpty()) {
				cw.add(ri.keyName)
				val type = namedValues[0].rawValue.data
				if ((type and ParserConstants.ATTR_TYPE_ENUM) != 0) {
					itemTag = "enum"
				} else if ((type and ParserConstants.ATTR_TYPE_FLAGS) != 0) {
					itemTag = "flag"
				}
				val formatValue = XmlGenUtils.getAttrTypeAsString(type)
				if (formatValue != null) {
					cw.add("\" format=\"").add(formatValue)
				}
				if (namedValues.size > 1) {
					for (rv in namedValues) {
						if (rv.nameRef == ParserConstants.ATTR_MIN) {
							cw.add("\" min=\"").add(rv.rawValue.data.toString())
							skipNamedValues = true
						}
					}
				}
			} else {
				cw.add(ri.keyName)
			}
			if (ri.typeName == "style" || ri.parentRef != 0) {
				cw.add("\" parent=\"")
				if (ri.parentRef != 0) {
					val parent = vp.decodeValue(ParserConstants.TYPE_REFERENCE, ri.parentRef)
					cw.add(checkNotNull(parent))
				}
			}
			cw.add("\">")

			if (!skipNamedValues) {
				cw.incIndent()
				for (value in namedValues) {
					addItem(cw, itemTag, ri.typeName, value)
				}
				cw.decIndent()
			}
			cw.startLine().add("</").add(ri.typeName).add('>')
		}
	}

	private fun addProtoItem(cw: ICodeWriter, itemTag: String, typeName: String, protoValue: ProtoValue) {
		val name = protoValue.name
		val value = protoValue.value
		when (typeName) {
			"attr" -> if (name != null) {
				addSimpleValue(cw, typeName, itemTag, name, value, "")
			}

			"style" -> if (name != null) {
				addSimpleValue(cw, typeName, itemTag, name, "", value)
			}

			"plurals" -> addSimpleValue(cw, typeName, itemTag, "quantity", name, value)

			else -> addSimpleValue(cw, typeName, itemTag, null, null, value)
		}
	}

	private fun addItem(cw: ICodeWriter, itemTag: String, typeName: String, value: RawNamedValue) {
		val nameStr = vp.decodeNameRef(value.nameRef)
		var valueStr = vp.decodeValue(value.rawValue)
		val dataType = value.rawValue.dataType

		if (typeName != "attr") {
			if (dataType == ParserConstants.TYPE_REFERENCE && (valueStr == null || valueStr == "0")) {
				valueStr = "@null"
			}
			if (dataType == ParserConstants.TYPE_INT_DEC && nameStr != null) {
				try {
					val intVal = Integer.parseInt(checkNotNull(valueStr))
					val newVal = manifestAttributes.decode(nameStr.replace("android:", "").replace("attr.", ""), intVal.toLong())
					if (newVal != null) {
						valueStr = newVal
					}
				} catch (e: NumberFormatException) {
					// ignore
				}
			}
			if (dataType == ParserConstants.TYPE_INT_HEX && nameStr != null) {
				try {
					val intVal = Integer.decode(checkNotNull(valueStr))
					val newVal = manifestAttributes.decode(nameStr.replace("android:", "").replace("attr.", ""), intVal.toLong())
					if (newVal != null) {
						valueStr = newVal
					}
				} catch (e: NumberFormatException) {
					// ignore
				}
			}
		}
		when (typeName) {
			"attr" -> if (nameStr != null) {
				addSimpleValue(cw, typeName, itemTag, nameStr, valueStr, "")
			}

			"style" -> if (nameStr != null) {
				addSimpleValue(cw, typeName, itemTag, nameStr, "", valueStr)
			}

			"plurals" -> {
				val quantity = ParserConstants.PLURALS_MAP[value.nameRef]
				addSimpleValue(cw, typeName, itemTag, "quantity", quantity, valueStr)
			}

			else -> addSimpleValue(cw, typeName, itemTag, null, null, valueStr)
		}
	}

	private fun addSimpleValue(cw: ICodeWriter, typeName: String, itemTag: String, attrName: String?, attrValue: String?, valueStr: String?) {
		if (valueStr == null) {
			return
		}
		if (valueStr.startsWith("res/")) {
			// remove duplicated resources.
			return
		}
		cw.startLine()
		cw.add('<').add(itemTag)
		if (attrName != null && attrValue != null) {
			if (typeName == "attr") {
				cw.add(' ').add("name=\"").add(attrName.replace("id.", "")).add("\" value=\"").add(attrValue).add('"')
			} else if (typeName == "style") {
				cw.add(' ').add("name=\"").add(attrName.replace("attr.", "")).add('"')
			} else {
				cw.add(' ').add(attrName).add("=\"").add(attrValue).add('"')
			}
		}

		if (itemTag == "string" && valueStr.contains("%") && StringFormattedCheck.hasMultipleNonPositionalSubstitutions(valueStr)) {
			cw.add(" formatted=\"false\"")
		}

		if (valueStr.isEmpty()) {
			cw.add(" />")
		} else {
			cw.add('>')
			if (itemTag == "string" || (typeName == "array" && valueStr[0] != '@')) {
				cw.add(StringUtils.escapeResStrValue(valueStr))
			} else {
				cw.add(StringUtils.escapeResValue(valueStr))
			}
			cw.add("</").add(itemTag).add('>')
		}
	}

	private fun getFileName(ri: ResourceEntry): String {
		val sb = StringBuilder()
		val qualifiers = ri.config
		sb.append("res/values")
		if (qualifiers.isNotEmpty()) {
			sb.append(qualifiers)
		}
		sb.append('/')
		sb.append(ri.typeName)
		if (!ri.typeName.endsWith("s")) {
			sb.append('s')
		}
		sb.append(".xml")
		return sb.toString()
	}

	companion object {
		/**
		 * Skip only file based resource type
		 */
		private val SKIP_RES_TYPES: Set<String> = setOf(
			"anim",
			"animator",
			"font",
			"id", // skip id type, it is usually auto generated when used this syntax "@+id/my_id"
			"interpolator",
			"layout",
			"menu",
			"mipmap",
			"navigation",
			"raw",
			"transition",
			"xml",
		)
	}
}
