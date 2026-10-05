package kadx.tests.integration.invoke

import kadx.NotYetImplemented
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 重载方法调用中的强制转换（String/List/ArrayList）：仅对必要处保留转换。
 */
class TestCastInOverloadedInvoke : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestCastInOverloadedInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("call(new ArrayList<>());")
			.containsOne("call((List<String>) new ArrayList());")
			.containsOne("call((String) obj);")
	}

	@NotYetImplemented
	@Test
	fun testNYI() {
		KadxAssertions.assertThat(getClassNode(TestCastInOverloadedInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("call((List<String>) new ArrayList<String>());")
	}
}
