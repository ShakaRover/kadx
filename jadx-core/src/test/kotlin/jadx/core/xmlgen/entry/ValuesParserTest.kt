package jadx.core.xmlgen.entry

import jadx.core.utils.android.AndroidResourcesMap
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

class ValuesParserTest {

	@Test
	fun testResMapLoad() {
		val androidResMap = AndroidResourcesMap.map
		assertThat(androidResMap).isNotNull().isNotEmpty()
	}
}
