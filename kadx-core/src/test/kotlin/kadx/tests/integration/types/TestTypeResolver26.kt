package kadx.tests.integration.types

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 原始类型集合与泛型集合混用：应插入 `(String)` 强转且不出现 "type inference failed"。
 */
class TestTypeResolver26 : IntegrationTest() {

	@Test
	fun test() {
		noDebugInfo()
		assertThat(getClassNode(TestTypeResolver26Fixture.TestCls::class.java))
			.code()
			.doesNotContain("type inference failed")
			.containsOne("this.target.add((String) this.source.get(0));")
	}
}
