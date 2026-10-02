package jadx.tests.integration.invoke

import jadx.NotYetImplemented
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 重载方法调用中的强制转换（String/List/ArrayList）：仅对必要处保留转换。
 */
class TestCastInOverloadedInvoke : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestCastInOverloadedInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("call(new ArrayList<>());")
			.containsOne("call((List<String>) new ArrayList());")
			.containsOne("call((String) obj);")
	}

	@NotYetImplemented
	@Test
	fun testNYI() {
		JadxAssertions.assertThat(getClassNode(TestCastInOverloadedInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("call((List<String>) new ArrayList<String>());")
	}
}
