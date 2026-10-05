package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.TestUtils.Companion.indent
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 匿名类实例初始化块与 run 方法分别对外部类字段赋值。
 */
class TestAnonymousClass4 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestAnonymousClass4Fixture.TestCls::class.java))
			.code()
			.containsOne(indent(3) + "new Thread() {")
			.containsOne(indent(4) + '{')
			.containsOne("f = 1;")
			.countString(2, indent(4) + '}')
			.containsOne(indent(4) + "public void run() {")
			.containsOne("d = 7.5")
			.containsOne(indent(3) + "}.start();")
	}
}
