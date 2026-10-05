package kadx.tests.integration.others

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 默认构造器保留：显式调用 super() 与空 static 块应被移除，但构造器本身要保留。
 */
class TestDefConstructorNotRemoved : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestDefConstructorNotRemovedFixture.TestCls::class.java))
			.code()
			.doesNotContain("super();")
			.doesNotContain("static {")
			.containsOne("public B() {")
	}
}
