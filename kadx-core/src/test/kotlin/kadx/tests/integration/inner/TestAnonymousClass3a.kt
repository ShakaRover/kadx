package kadx.tests.integration.inner

import kadx.NotYetImplemented
import kadx.api.CommentsLevel
import kadx.tests.api.IntegrationTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类通过自增/自减访问外部类私有字段（NotYetImplemented）。
 */
class TestAnonymousClass3a : IntegrationTest() {

	@Test
	@NotYetImplemented
	fun test() {
		getArgs().commentsLevel = CommentsLevel.NONE
		assertThat(getClassNode(TestAnonymousClass3aFixture.TestCls::class.java))
			.code()
			.doesNotContain("synthetic")
			.doesNotContain("access$00")
			.doesNotContain("AnonymousClass_")
			.doesNotContain("unused = ")
			.containsLine(4, "public void run() {")
			.containsLine(3, "}.run();")
	}
}
