package jadx.tests.integration.invoke

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * 继承层次中的重载方法调用：`b.call(...)` 的参数转换应正确还原。
 */
class TestHierarchyOverloadedInvoke : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestHierarchyOverloadedInvokeFixture.TestCls::class.java))
			.code()
			.containsOne("b.call(new ArrayList<>());")
			.containsOne("b.call((List<String>) new ArrayList());")
			.containsOne("b.call((String) obj);")
	}
}
