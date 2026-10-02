package jadx.tests.integration.types

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 常量类型推断：`equals` 中的 `this`/`null` 比较应还原为 `obj == this` / `obj == null`。
 */
class TestConstTypeInference : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestConstTypeInferenceFixture.TestCls::class.java))
			.code()
			.containsOne("obj == this")
			.containsOneOf("obj == null", "obj != null")
	}

	@Test
	fun test2() {
		noDebugInfo()
		getClassNode(TestConstTypeInferenceFixture.TestCls::class.java)
	}
}
