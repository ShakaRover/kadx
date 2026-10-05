package kadx.core.utils.log

import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

class LogUtilsTest {

	@Test
	fun escape() {
		val src = "a.b,c:d;e disallowed\"a'b#c*d\te\rf\ng"
		val out = "a.b,c:d;e disallowed.a.b.c.d.e.f.g"
		assertThat(LogUtils.escape(src)).isEqualTo(out)
	}
}
