package jadx.tests.integration.trycatch

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * try 内的 if 分支提前 return：if 块与其后代码不应被错误移出 try。
 */
class TestIfInTryCatch : IntegrationTest() {

	@Test
	fun test() {
		// if ifBlock is moved out of try, there will be uncaught exception
		JadxAssertions.assertThat(getClassNode(TestIfInTryCatchFixture.TestCls::class.java))
			.code()
	}
}
