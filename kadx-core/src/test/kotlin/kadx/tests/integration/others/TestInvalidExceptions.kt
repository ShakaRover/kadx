package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 非法异常声明：字节码篡改被检测到时应跳过非法 throws 并给出提示。
 */
class TestInvalidExceptions : SmaliTest() {

	@Test
	fun test() {
		allowWarnInCode()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("invalidException() throws FileNotFoundException {")
			.containsOne("Byte code manipulation detected: skipped illegal throws declaration")
			.removeBlockComments()
			.doesNotContain("String")
	}
}
