package kadx.core.xmlgen.entry

import kadx.core.utils.android.AndroidResourcesMap
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

class ValuesParserTest {

	@Test
	fun testResMapLoad() {
		val androidResMap = AndroidResourcesMap.map
		assertThat(androidResMap).isNotNull().isNotEmpty()
	}
}
