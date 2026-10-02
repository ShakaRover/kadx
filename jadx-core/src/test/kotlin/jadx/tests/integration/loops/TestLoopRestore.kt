package jadx.tests.integration.loops

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
