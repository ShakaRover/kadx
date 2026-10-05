package kadx.tests.integration.trycatch

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * try 内的 if 分支提前 return：if 块与其后代码不应被错误移出 try。
 */
class TestIfInTryCatch : IntegrationTest() {

	@Test
	fun test() {
		// if ifBlock is moved out of try, there will be uncaught exception
		KadxAssertions.assertThat(getClassNode(TestIfInTryCatchFixture.TestCls::class.java))
			.code()
	}
}
