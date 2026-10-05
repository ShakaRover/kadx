package kadx.tests.integration.trycatch

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 三个空的 Error catch 块应各自保留。
 */
class TestTryWithEmptyCatchTriple : SmaliTest() {
	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			// all catches are empty
			.containsLines(2, "} catch (Error unused) {", "}")
			.containsLines(2, "} catch (Error unused2) {", "}")
			.containsLines(2, "} catch (Error unused3) {", "}")
	}
}
