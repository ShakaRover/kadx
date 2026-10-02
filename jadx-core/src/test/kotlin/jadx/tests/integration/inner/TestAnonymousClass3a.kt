package jadx.tests.integration.inner

import jadx.NotYetImplemented
import jadx.api.CommentsLevel
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
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
