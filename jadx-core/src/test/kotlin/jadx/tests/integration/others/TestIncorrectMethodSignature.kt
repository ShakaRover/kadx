package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
