package kadx.tests.integration.invoke

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型方法与变参方法重载：`c.add(i)` 不应被加上 `(Container)` 转换。
 */
class TestOverloadedMethodInvoke2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestOverloadedMethodInvoke2Fixture.AbstractItem::class.java))
			.code().containsOne("c.add(i);")
			.doesNotContain("(Container)")
	}
}
