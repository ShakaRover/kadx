package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 匿名类访问外部类字段与 `Outer.this`：字段赋值与内部类实例引用应正确还原。
 */
class TestAnonymousClass2 : IntegrationTest() {

	@Test
	fun test() {
		assertThat(getClassNode(TestAnonymousClass2Fixture.TestCls::class.java))
			.code()
			.doesNotContain("synthetic")
			.doesNotContain("AnonymousClass_")
			.contains("f = 1;")
			.contains("f = i;")
			.doesNotContain("Inner obj = ;")
			.contains("Inner.this;")
	}
}
