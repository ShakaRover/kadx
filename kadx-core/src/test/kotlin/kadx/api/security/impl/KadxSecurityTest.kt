package kadx.api.security.impl

import kadx.api.security.KadxSecurityFlag
import kadx.api.security.SanitizeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class KadxSecurityTest {
	private val security = KadxSecurity(KadxSecurityFlag.all())

	@Test
	fun sanitizeString() {
		verifySanitize("ab';c", "abc")
		verifySanitize("a/b\\c", "abc")
		verifySanitize("\"'{}[]:>?*|", "")
	}

	private fun verifySanitize(inputStr: String, expectedStr: String) {
		val result = security.sanitizeString(inputStr, SanitizeType.GRADLE_GROOVY)
		LOG.debug("sanitize {}, result: {}", inputStr, result)
		assertThat(result)
			.describedAs("Sanitized string of %s expected to be %s, but got: %s", inputStr, expectedStr, result)
			.isEqualTo(expectedStr)
	}

	companion object {
		private val LOG = LoggerFactory.getLogger(KadxSecurityTest::class.java)
	}
}
