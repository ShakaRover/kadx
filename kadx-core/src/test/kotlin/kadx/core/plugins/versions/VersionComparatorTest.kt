package kadx.core.plugins.versions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VersionComparatorTest {

	@Test
	fun testCompare() {
		checkCompare("", "", 0)
		checkCompare("1", "1", 0)
		checkCompare("1", "2", -1)
		checkCompare("1.1", "1.1", 0)
		checkCompare("0.5", "0.5", 0)
		checkCompare("0.5", "0.5.0", 0)
		checkCompare("0.5", "0.5.00", 0)
		checkCompare("0.5", "0.5.0.0", 0)
		checkCompare("0.5", "0.5.0.1", -1)
		checkCompare("0.5.0", "0.5.0", 0)
		checkCompare("0.5.0", "0.5.1", -1)
		checkCompare("0.5", "0.5.1", -1)
		checkCompare("0.4.8", "0.5", -1)
		checkCompare("0.4.8", "0.5.0", -1)
		checkCompare("0.4.8", "0.6", -1)
		checkCompare("1.3.3.1", "1.3.3", 1)
		checkCompare("1.3.3-1", "1.3.3", 1)
		checkCompare("1.3.3.1-1", "1.3.3", 1)
	}

	@Test
	fun testCompareUnstable() {
		checkCompare("r2190.ce527ed", "kadx-r2299.742d30d", -1)
	}

	companion object {
		private fun checkCompare(a: String, b: String, result: Int) {
			assertThat(VersionComparator.checkAndCompare(a, b))
				.`as`("Compare %s and %s expect %d", a, b, result)
				.isEqualTo(result)
			assertThat(VersionComparator.checkAndCompare(b, a))
				.`as`("Compare %s and %s expect %d", b, a, -result)
				.isEqualTo(-result)
		}
	}
}
