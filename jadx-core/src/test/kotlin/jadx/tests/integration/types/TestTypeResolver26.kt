package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
