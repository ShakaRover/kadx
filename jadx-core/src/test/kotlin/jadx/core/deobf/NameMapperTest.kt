package jadx.core.deobf

import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

class NameMapperTest {

	@Test
	fun validIdentifiers() {
		assertThat(NameMapper.isValidIdentifier("ACls")).isTrue()
	}

	@Test
	fun notValidIdentifiers() {
		assertThat(NameMapper.isValidIdentifier("1cls")).isFalse()
		assertThat(NameMapper.isValidIdentifier("-cls")).isFalse()
		assertThat(NameMapper.isValidIdentifier("A-cls")).isFalse()
	}

	@Test
	fun testRemoveInvalidCharsMiddle() {
		assertThat(NameMapper.removeInvalidCharsMiddle("1cls")).isEqualTo("1cls")
		assertThat(NameMapper.removeInvalidCharsMiddle("-cls")).isEqualTo("cls")
		assertThat(NameMapper.removeInvalidCharsMiddle("A-cls")).isEqualTo("Acls")
	}

	@Test
	fun testRemoveInvalidChars() {
		assertThat(NameMapper.removeInvalidChars("1cls", "C")).isEqualTo("C1cls")
		assertThat(NameMapper.removeInvalidChars("-cls", "C")).isEqualTo("cls")
		assertThat(NameMapper.removeInvalidChars("A-cls", "C")).isEqualTo("Acls")
	}
}
