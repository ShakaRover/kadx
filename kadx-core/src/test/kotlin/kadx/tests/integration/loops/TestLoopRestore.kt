package kadx.tests.integration.loops

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 循环还原：try 块中的字节数组 for-each。
 */
class TestLoopRestore : SmaliTest() {

	@Test
	fun test() {
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("try {")
			.containsOne("for (byte b : bArrDigest) {")
	}
}
