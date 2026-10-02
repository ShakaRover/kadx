package jadx.tests.integration.generics

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 泛型接口实现：不同实现类覆写后的签名应各自保留，且共 4 个 `@Override`。
 */
class TestGenericsMthOverride : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestGenericsMthOverrideFixture.TestCls::class.java))
			.code()
			.containsOne("public Y method(X x) {")
			.containsOne("public Y method(Object x) {")
			.containsOne("public Y method(Exception x) {")
			.containsOne("public Object method(Object x) {")
			.countString(4, "@Override")
	}
}
