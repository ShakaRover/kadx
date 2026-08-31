package jadx.plugins.input.aab.parsers

import com.android.aapt.ConfigurationOuterClass
import com.android.aapt.Resources
import jadx.core.xmlgen.ParserConstants
import jadx.core.xmlgen.XmlGenUtils
import jadx.core.xmlgen.entry.EntryConfig
import jadx.core.xmlgen.entry.ProtoValue

/**
 * AAB protobuf 资源的公共解析基类。
 *
 * **背景**：把 aapt2 的 protobuf 类型（Style/Array/Attribute/Plural/CompoundValue/Item/Configuration）
 * 转成 jadx 内部的 [ProtoValue] / 配置限定符字符串，供 ResTableProtoParser 与 ResXmlProtoParser 复用。
 */
public open class CommonProtoParser : ParserConstants() {

	protected open fun parse(s: Resources.Style): ProtoValue {
		val namedValues = ArrayList<ProtoValue>(s.entryCount)
		var parent: String? = s.parent.name
		if (parent == null || parent.isEmpty()) {
			parent = null
		} else {
			parent = "@" + parent
		}
		for (i in 0 until s.entryCount) {
			val entry = s.getEntry(i)
			val name = entry.key.name
			val value = parse(entry.item)
			namedValues.add(ProtoValue(value).setName(name))
		}
		return ProtoValue().setNamedValues(namedValues).setParent(parent)
	}

	protected open fun parse(s: Resources.Styleable): ProtoValue {
		val namedValues = ArrayList<ProtoValue>(s.entryCount)
		for (i in 0 until s.entryCount) {
			val e = s.getEntry(i)
			namedValues.add(ProtoValue("@" + e.attr.name))
		}
		return ProtoValue().setNamedValues(namedValues)
	}

	protected open fun parse(a: Resources.Array): ProtoValue {
		val namedValues = ArrayList<ProtoValue>(a.elementCount)
		for (i in 0 until a.elementCount) {
			val e = a.getElement(i)
			val value = parse(e.item)
			namedValues.add(ProtoValue(value))
		}
		return ProtoValue().setNamedValues(namedValues)
	}

	protected open fun parse(a: Resources.Attribute): ProtoValue {
		val format = XmlGenUtils.getAttrTypeAsString(a.formatFlags)
		val namedValues = ArrayList<ProtoValue>(a.symbolCount)
		for (i in 0 until a.symbolCount) {
			val s = a.getSymbol(i)
			val type = s.type
			val name = s.name.name
			val value = s.value.toString()
			namedValues.add(ProtoValue(value).setName(name).setType(type))
		}
		return ProtoValue(format).setNamedValues(namedValues)
	}

	protected open fun parse(p: Resources.Plural): ProtoValue {
		val namedValues = ArrayList<ProtoValue>(p.entryCount)
		for (i in 0 until p.entryCount) {
			val e = p.getEntry(i)
			val name = e.arity.name
			val value = parse(e.item)
			namedValues.add(ProtoValue(value).setName(name))
		}
		return ProtoValue().setNamedValues(namedValues)
	}

	protected open fun parse(c: Resources.CompoundValue): ProtoValue = when (c.valueCase) {
		Resources.CompoundValue.ValueCase.STYLE -> parse(c.style)
		Resources.CompoundValue.ValueCase.STYLEABLE -> parse(c.styleable)
		Resources.CompoundValue.ValueCase.ARRAY -> parse(c.array)
		Resources.CompoundValue.ValueCase.ATTR -> parse(c.attr)
		Resources.CompoundValue.ValueCase.PLURAL -> parse(c.plural)
		else -> ProtoValue("Unresolved value")
	}

	protected open fun parse(c: ConfigurationOuterClass.Configuration): String {
		var language = c.locale.toCharArray()
		if (language.isEmpty()) {
			language = "\u0000".toCharArray()
		}
		val mcc = c.mcc.toShort()
		val mnc = c.mnc.toShort()
		val orientation = c.orientationValue.toByte()
		val screenWidth = c.screenWidth.toShort()
		val screenHeight = c.screenHeight.toShort()
		val screenWidthDp = c.screenWidthDp.toShort()
		val screenHeightDp = c.screenHeightDp.toShort()
		val smallestScreenWidthDp = c.smallestScreenWidthDp.toShort()
		val sdkVersion = c.sdkVersion.toShort()
		val keyboard = c.keyboardValue.toByte()
		val touchscreen = c.touchscreenValue.toByte()
		val density = c.density
		val screenLayout = c.screenLayoutLongValue.toByte()
		val colorMode = (c.hdrValue or c.wideColorGamutValue).toByte()
		val screenLayout2 = (c.layoutDirectionValue or c.screenRoundValue).toByte()
		val navigation = c.navigationValue.toByte()
		val inputFlags = (c.keysHiddenValue or c.navHiddenValue).toByte()
		val grammaticalInflection = c.grammaticalGenderValue.toByte()
		val size = c.serializedSize
		val uiMode = (c.uiModeNightValue or c.uiModeTypeValue).toByte()

		c.screenLayoutSize // unknown field
		c.product // unknown field

		return EntryConfig(
			mcc, mnc, language, "\u0000".toCharArray(),
			orientation, touchscreen, density, keyboard, navigation,
			inputFlags, grammaticalInflection, screenWidth, screenHeight, sdkVersion,
			screenLayout, uiMode, smallestScreenWidthDp, screenWidthDp,
			screenHeightDp, "\u0000".toCharArray(), "\u0000".toCharArray(), screenLayout2,
			colorMode, false, size,
		).qualifiers
	}

	protected open fun parse(i: Resources.Item): String? {
		if (i.hasRawStr()) {
			return i.rawStr.value
		}
		if (i.hasStr()) {
			return i.str.value
		}
		if (i.hasStyledStr()) {
			return i.styledStr.value
		}
		if (i.hasPrim()) {
			val prim = i.prim
			return when (prim.oneofValueCase) {
				Resources.Primitive.OneofValueCase.NULL_VALUE -> null
				Resources.Primitive.OneofValueCase.INT_DECIMAL_VALUE -> prim.intDecimalValue.toString()
				Resources.Primitive.OneofValueCase.INT_HEXADECIMAL_VALUE -> Integer.toHexString(prim.intHexadecimalValue)
				Resources.Primitive.OneofValueCase.BOOLEAN_VALUE -> prim.booleanValue.toString()
				Resources.Primitive.OneofValueCase.FLOAT_VALUE -> prim.floatValue.toString()
				Resources.Primitive.OneofValueCase.COLOR_ARGB4_VALUE -> String.format("#%04x", prim.colorArgb4Value)
				Resources.Primitive.OneofValueCase.COLOR_ARGB8_VALUE -> String.format("#%08x", prim.colorArgb8Value)
				Resources.Primitive.OneofValueCase.COLOR_RGB4_VALUE -> String.format("#%03x", prim.colorRgb4Value)
				Resources.Primitive.OneofValueCase.COLOR_RGB8_VALUE -> String.format("#%06x", prim.colorRgb8Value)
				Resources.Primitive.OneofValueCase.DIMENSION_VALUE -> XmlGenUtils.decodeComplex(prim.dimensionValue, false)
				Resources.Primitive.OneofValueCase.FRACTION_VALUE -> XmlGenUtils.decodeComplex(prim.dimensionValue, true)
				else -> "" // EMPTY_VALUE 及其他未处理情况（含 DEPRECATED/NOT_SET）
			}
		}
		if (i.hasRef()) {
			val ref = i.ref
			var value = ref.name
			if (value.isEmpty()) {
				value = "id/" + ref.id
			}
			return "@" + value
		}
		if (i.hasFile()) {
			return i.file.path
		}
		return ""
	}
}
