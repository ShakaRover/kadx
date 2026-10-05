package kadx.core.xmlgen

import kadx.api.KadxArgs
import kadx.api.security.IKadxSecurity
import kadx.api.security.KadxSecurityFlag
import kadx.api.security.impl.KadxSecurity
import kadx.core.xmlgen.entry.RawNamedValue
import kadx.core.xmlgen.entry.RawValue
import kadx.core.xmlgen.entry.ResourceEntry
import kadx.core.xmlgen.entry.ValuesParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResXmlGenTest {
	private val args = KadxArgs()
	private val security: IKadxSecurity = KadxSecurity(KadxSecurityFlag.all())
	private val manifestAttributes = ManifestAttributes(security)

	@BeforeEach
	fun init() {
		args.codeNewLineStr = "\n"
	}

	@Test
	fun testSimpleAttr() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "kadx.gui.app", "attr", "size", "")
		re.namedValues = listOf(RawNamedValue(16777216, RawValue(16, 64)))
		resStorage.add(re)

		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].name).isEqualTo("res/values/attrs.xml")
		val input = files[0].text.toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <attr name=\"size\" format=\"dimension\">\n" +
				"    </attr>\n" +
				"</resources>",
		)
	}

	@Test
	fun testAttrEnum() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "kadx.gui.app", "attr", "size", "")
		re.namedValues =
			listOf(
				RawNamedValue(0x01000000, RawValue(16, 65536)),
				RawNamedValue(0x01040000, RawValue(16, 1)),
			)
		resStorage.add(re)

		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].name).isEqualTo("res/values/attrs.xml")
		val input = files[0].text.toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <attr name=\"size\">\n" +
				"        <enum name=\"android:string.cancel\" value=\"1\" />\n" +
				"    </attr>\n" +
				"</resources>",
		)
	}

	@Test
	fun testAttrFlag() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "kadx.gui.app", "attr", "size", "")
		re.namedValues =
			listOf(
				RawNamedValue(0x01000000, RawValue(16, 131072)),
				RawNamedValue(0x01040000, RawValue(16, 1)),
			)
		resStorage.add(re)

		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].name).isEqualTo("res/values/attrs.xml")
		val input = files[0].text.toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <attr name=\"size\">\n" +
				"        <flag name=\"android:string.cancel\" value=\"1\" />\n" +
				"    </attr>\n" +
				"</resources>",
		)
	}

	@Test
	fun testAttrMin() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "kadx.gui.app", "attr", "size", "")
		re.namedValues =
			listOf(RawNamedValue(16777216, RawValue(16, 4)), RawNamedValue(16777217, RawValue(16, 1)))
		resStorage.add(re)

		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].name).isEqualTo("res/values/attrs.xml")
		val input = files[0].text.toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <attr name=\"size\" format=\"integer\" min=\"1\">\n" +
				"    </attr>\n" +
				"</resources>",
		)
	}

	@Test
	fun testStyle() {
		val resStorage = ResourceStorage(security)
		var re = ResourceEntry(2130903103, "kadx.gui.app", "style", "KadxGui", "")
		re.namedValues = listOf(RawNamedValue(16842836, RawValue(1, 17170445)))
		resStorage.add(re)

		re = ResourceEntry(2130903104, "kadx.gui.app", "style", "KadxGui.Dialog", "")
		re.parentRef = 2130903103
		re.namedValues = arrayListOf()
		resStorage.add(re)
		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].name).isEqualTo("res/values/styles.xml")
		val input = files[0].text.toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <style name=\"KadxGui\" parent=\"\">\n" +
				"        <item name=\"android:windowBackground\">@android:color/transparent</item>\n" +
				"    </style>\n" +
				"    <style name=\"KadxGui.Dialog\" parent=\"@style/KadxGui\">\n" +
				"    </style>\n" +
				"</resources>",
		)
	}

	@Test
	fun testString() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "kadx.gui.app", "string", "app_name", "")
		re.simpleValue = RawValue(3, 0)
		re.namedValues = listOf()
		resStorage.add(re)

		val strings = BinaryXMLStrings()
		strings.put(0, "Kadx Decompiler App")
		val vp = ValuesParser(strings, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].name).isEqualTo("res/values/strings.xml")
		val input = files[0].text.toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <string name=\"app_name\">Kadx Decompiler App</string>\n" +
				"</resources>",
		)
	}

	@Test
	fun testStringFormattedFalse() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "kadx.gui.app", "string", "app_name", "")
		re.simpleValue = RawValue(3, 0)
		re.namedValues = listOf()
		resStorage.add(re)

		val strings = BinaryXMLStrings()
		strings.put(0, "%s at %s")
		val vp = ValuesParser(strings, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].name).isEqualTo("res/values/strings.xml")
		val input = files[0].text.toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <string name=\"app_name\" formatted=\"false\">%s at %s</string>\n" +
				"</resources>",
		)
	}

	@Test
	fun testArrayEscape() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "kadx.gui.app", "array", "single_quote_escape_sample", "")
		re.namedValues =
			listOf(RawNamedValue(16777216, RawValue(3, 0)))
		resStorage.add(re)

		val strings = BinaryXMLStrings()
		strings.put(0, "Let's go")
		val vp = ValuesParser(strings, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].name).isEqualTo("res/values/arrays.xml")
		val input = files[0].text.toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <array name=\"single_quote_escape_sample\">\n" +
				"        <item>Let\\'s go</item>\n" +
				"    </array>\n" +
				"</resources>",
		)
	}
}
