package jadx.tests.integration.rename

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名内部类内联：Runnable 的匿名实现应保留为 `new Runnable() {`，且不出现 `AnonymousClass1`。
 */
class TestAnonymousInline : IntegrationTest() {

	@Test
	fun test() {
		val cls = getClassNode(TestAnonymousInlineFixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.containsOnlyOnce("return new Runnable() {")

		assertThat(cls)
			.reloadCode(this)
			.removeBlockComments() // remove comment about inlined class
			.containsOnlyOnce("return new Runnable() {")
			.doesNotContain("AnonymousClass1")
	}
}
