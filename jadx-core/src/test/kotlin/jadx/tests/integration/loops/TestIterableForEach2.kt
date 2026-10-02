package jadx.tests.integration.loops

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 遍历方法调用返回的 Iterable 应还原为 for-each。
 */
class TestIterableForEach2 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestIterableForEach2Fixture.TestCls::class.java))
			.code()
			.containsOne("for (Authorization auth : service.getAuthorizations()) {")
			.containsOne("if (isValid(auth)) {")
			.containsOne("return auth.getToken();")
	}
}
