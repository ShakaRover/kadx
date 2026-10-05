package kadx.tests.integration.inner

import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * `FilenameFilter` 匿名类：默认内联为匿名类，关闭内联后应还原为具名 `AnonymousClass1`。
 */
class TestAnonymousClass : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestAnonymousClassFixture.TestCls::class.java))
			.code()
			.contains("new File(\"a\").list(new FilenameFilter()")
			.doesNotContain("synthetic")
			.doesNotContain("this")
			.doesNotContain("null")
			.doesNotContain("AnonymousClass_")
			.doesNotContain("class AnonymousClass")
	}

	@Test
	fun testNoInline() {
		getArgs().isInlineAnonymousClasses = false
		assertThat(getClassNode(TestAnonymousClassFixture.TestCls::class.java))
			.code()
			.contains("class AnonymousClass1 implements FilenameFilter {")
			.containsOne("new AnonymousClass1()")
	}
}
