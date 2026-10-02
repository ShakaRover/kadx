package jadx.core.xmlgen

import jadx.api.JadxArgs
import jadx.api.security.IJadxSecurity
import jadx.api.security.JadxSecurityFlag
import jadx.api.security.impl.JadxSecurity
import jadx.core.xmlgen.entry.RawNamedValue
import jadx.core.xmlgen.entry.RawValue
import jadx.core.xmlgen.entry.ResourceEntry
import jadx.core.xmlgen.entry.ValuesParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResXmlGenTest {
	private val args = JadxArgs()
	private val security: IJadxSecurity = JadxSecurity(JadxSecurityFlag.all())
	private val manifestAttributes = ManifestAttributes(security)

	@BeforeEach
	fun init() {
		args.codeNewLineStr = "\n"
	}

	@Test
	fun testSimpleAttr() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "jadx.gui.app", "attr", "size", "")
		re.setNamedValues(listOf(RawNamedValue(16777216, RawValue(16, 64))))
		resStorage.add(re)

		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].getName()).isEqualTo("res/values/attrs.xml")
		val input = files[0].getText().toString()
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
		val re = ResourceEntry(2130903103, "jadx.gui.app", "attr", "size", "")
		re.setNamedValues(
			listOf(
				RawNamedValue(0x01000000, RawValue(16, 65536)),
				RawNamedValue(0x01040000, RawValue(16, 1)),
			),
		)
		resStorage.add(re)

		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].getName()).isEqualTo("res/values/attrs.xml")
		val input = files[0].getText().toString()
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
		val re = ResourceEntry(2130903103, "jadx.gui.app", "attr", "size", "")
		re.setNamedValues(
			listOf(
				RawNamedValue(0x01000000, RawValue(16, 131072)),
				RawNamedValue(0x01040000, RawValue(16, 1)),
			),
		)
		resStorage.add(re)

		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].getName()).isEqualTo("res/values/attrs.xml")
		val input = files[0].getText().toString()
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
		val re = ResourceEntry(2130903103, "jadx.gui.app", "attr", "size", "")
		re.setNamedValues(
			listOf(RawNamedValue(16777216, RawValue(16, 4)), RawNamedValue(16777217, RawValue(16, 1))),
		)
		resStorage.add(re)

		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].getName()).isEqualTo("res/values/attrs.xml")
		val input = files[0].getText().toString()
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
		var re = ResourceEntry(2130903103, "jadx.gui.app", "style", "JadxGui", "")
		re.setNamedValues(listOf(RawNamedValue(16842836, RawValue(1, 17170445))))
		resStorage.add(re)

		re = ResourceEntry(2130903104, "jadx.gui.app", "style", "JadxGui.Dialog", "")
		re.setParentRef(2130903103)
		re.setNamedValues(arrayListOf())
		resStorage.add(re)
		val vp = ValuesParser(null, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].getName()).isEqualTo("res/values/styles.xml")
		val input = files[0].getText().toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <style name=\"JadxGui\" parent=\"\">\n" +
				"        <item name=\"android:windowBackground\">@android:color/transparent</item>\n" +
				"    </style>\n" +
				"    <style name=\"JadxGui.Dialog\" parent=\"@style/JadxGui\">\n" +
				"    </style>\n" +
				"</resources>",
		)
	}

	@Test
	fun testString() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "jadx.gui.app", "string", "app_name", "")
		re.setSimpleValue(RawValue(3, 0))
		re.setNamedValues(listOf())
		resStorage.add(re)

		val strings = BinaryXMLStrings()
		strings.put(0, "Jadx Decompiler App")
		val vp = ValuesParser(strings, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].getName()).isEqualTo("res/values/strings.xml")
		val input = files[0].getText().toString()
		assertThat(input).isEqualTo(
			"<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
				"<resources>\n" +
				"    <string name=\"app_name\">Jadx Decompiler App</string>\n" +
				"</resources>",
		)
	}

	@Test
	fun testStringFormattedFalse() {
		val resStorage = ResourceStorage(security)
		val re = ResourceEntry(2130903103, "jadx.gui.app", "string", "app_name", "")
		re.setSimpleValue(RawValue(3, 0))
		re.setNamedValues(listOf())
		resStorage.add(re)

		val strings = BinaryXMLStrings()
		strings.put(0, "%s at %s")
		val vp = ValuesParser(strings, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].getName()).isEqualTo("res/values/strings.xml")
		val input = files[0].getText().toString()
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
		val re = ResourceEntry(2130903103, "jadx.gui.app", "array", "single_quote_escape_sample", "")
		re.setNamedValues(
			listOf(RawNamedValue(16777216, RawValue(3, 0))),
		)
		resStorage.add(re)

		val strings = BinaryXMLStrings()
		strings.put(0, "Let's go")
		val vp = ValuesParser(strings, resStorage.resourcesNames)
		val resXmlGen = ResXmlGen(resStorage, vp, manifestAttributes)
		val files = resXmlGen.makeResourcesXml(args)

		assertThat(files).hasSize(1)
		assertThat(files[0].getName()).isEqualTo("res/values/arrays.xml")
		val input = files[0].getText().toString()
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
