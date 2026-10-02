package jadx.tests.integration.inner

import jadx.api.CommentsLevel
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.TestUtils.Companion.indent
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 嵌套匿名类：内层匿名类调用外层匿名类的私有方法，验证匿名类内联/非内联两种模式。
 */
class TestAnonymousClass18 : IntegrationTest() {

	@Test
	fun test() {
		getArgs().commentsLevel = CommentsLevel.WARN
		assertThat(getClassNode(TestAnonymousClass18Fixture.TestCls::class.java))
			.code()
			.doesNotContain("AnonymousClass1.this")
			.doesNotContain("class AnonymousClass1")
			// .doesNotContain("TestAnonymousClass18$TestCls.runJob(") // TODO: ???
			.containsOne(indent() + "doSomething();")
	}

	@Test
	fun testNoInline() {
		getArgs().isInlineAnonymousClasses = false
		assertThat(getClassNode(TestAnonymousClass18Fixture.TestCls::class.java))
			.code()
			.containsOne("class AnonymousClass1 implements Job {")
			.containsOne("class C00001 implements Job {")
			.containsOne("AnonymousClass1.this.doSomething();")
	}
}
