package jadx.tests.integration.inner

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 匿名类作为字段初始值并被其它匿名类捕获：泛型构造与 `this.c` 引用应正确还原。
 */
class TestAnonymousClass9 : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestAnonymousClass9Fixture.TestCls::class.java))
			.code()
			.containsOne("c = new Callable<String>() {")
			.containsOne("return new FutureTask<String>(this.c) {")
			.doesNotContain("synthetic")
	}
}
