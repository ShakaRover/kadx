package kadx.tests.integration.loops

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions
import org.junit.jupiter.api.Test

/**
 * 遍历方法调用返回的 Iterable 应还原为 for-each。
 */
class TestIterableForEach2 : IntegrationTest() {

	@Test
	fun test() {
		KadxAssertions.assertThat(getClassNode(TestIterableForEach2Fixture.TestCls::class.java))
			.code()
			.containsOne("for (Authorization auth : service.getAuthorizations()) {")
			.containsOne("if (isValid(auth)) {")
			.containsOne("return auth.getToken();")
	}
}
