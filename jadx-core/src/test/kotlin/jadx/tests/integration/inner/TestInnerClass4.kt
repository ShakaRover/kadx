package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 私有构造器的内部类：字段访问应内联为 `new C().c`。
 */
class TestInnerClass4 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestInnerClass4Fixture.TestCls::class.java))
			.code()
			.containsOne("return new C().c;")
	}
}
