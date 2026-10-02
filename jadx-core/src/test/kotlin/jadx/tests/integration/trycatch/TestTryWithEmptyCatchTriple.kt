package jadx.tests.integration.trycatch

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
