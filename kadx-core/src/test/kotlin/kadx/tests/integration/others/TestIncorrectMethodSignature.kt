package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue #858：错误的方法签名会改变参数类型与寄存器编号，应正确还原构造器签名。
 */
class TestIncorrectMethodSignature : SmaliTest() {

	@Test
	fun test() {
		allowWarnInCode()
		assertThat(getClassNodeFromSmali())
			.code()
			.containsOne("public TestIncorrectMethodSignature(String str) {")
	}
}
