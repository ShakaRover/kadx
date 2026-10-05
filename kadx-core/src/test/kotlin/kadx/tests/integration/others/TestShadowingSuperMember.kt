package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 遮蔽父类成员：子类字段与父类字段同名时，`super.` 与显式强转访问应分别输出。
 */
class TestShadowingSuperMember : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestShadowingSuperMemberFixture.TestCls::class.java))
			.code()
			.containsOne("return super.a00 + b;")
			.containsOne("return super.a00 - b;")
			.containsOne("((A) b).a00 = 2;")
	}
}
