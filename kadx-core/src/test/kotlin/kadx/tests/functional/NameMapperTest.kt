package kadx.tests.functional

import kadx.core.deobf.NameMapper
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [NameMapper.isValidFullIdentifier] 对合法/非法全限定名的判定。
 */
class NameMapperTest {

	@Test
	fun testValidFullIdentifiers() {
		val validNames = arrayOf(
			"C",
			"Cc",
			"b.C",
			"b.Cc",
			"aAa.b.Cc",
			"a.b.Cc",
			"a.b.C_c",
			"a.b.C\$c",
			"a.b.C9",
		)
		for (validName in validNames) {
			assertThat(NameMapper.isValidFullIdentifier(validName)).isTrue()
		}
	}

	@Test
	fun testInvalidFullIdentifiers() {
		val invalidNames = arrayOf(
			"",
			"5",
			"7A",
			".C",
			"b.9C",
			"b..C",
		)
		for (invalidName in invalidNames) {
			assertThat(NameMapper.isValidFullIdentifier(invalidName)).isFalse()
		}
	}
}
