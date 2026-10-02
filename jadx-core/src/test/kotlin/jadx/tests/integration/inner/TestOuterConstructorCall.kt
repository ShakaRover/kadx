package jadx.tests.integration.inner

import jadx.api.CommentsLevel
import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 内部类调用外部类私有构造器：应还原为 `new Outer$TestCls(this)`，不残留合成字段。
 */
class TestOuterConstructorCall : IntegrationTest() {

	@Test
	fun test() {
		getArgs().commentsLevel = CommentsLevel.WARN
		assertThat(getClassNode(TestOuterConstructorCallFixture.TestCls::class.java))
			.code()
			.containsOne("class Inner {")
			.containsOne("return new TestOuterConstructorCallFixture\$TestCls(this);")
			.doesNotContain("synthetic", "this\$0")
	}
}
