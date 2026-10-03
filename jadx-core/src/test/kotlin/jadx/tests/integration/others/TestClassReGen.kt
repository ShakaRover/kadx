package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 类重新生成：修改类名 / 方法别名 / 字段别名后，重新反编译的代码应反映新名称。
 */
class TestClassReGen : IntegrationTest() {

	@Test
	fun test() {
		val cls = getClassNode(TestClassReGenFixture.TestCls::class.java)
		assertThat(cls)
			.code()
			.containsOnlyOnce("private int intField = 5;")
			.containsOnlyOnce("public static class A {")
			.containsOnlyOnce("public int test() {")

		cls.innerClasses[0].classInfo.changeShortName("ARenamed")
		checkNotNull(cls.searchMethodByShortName("test")).methodInfo.alias = "testRenamed"
		checkNotNull(cls.searchFieldByName("intField")).getFieldInfo().alias = "intFieldRenamed"

		assertThat(cls)
			.reloadCode(this)
			.containsOnlyOnce("private int intFieldRenamed = 5;")
			.containsOnlyOnce("public static class ARenamed {")
			.containsOnlyOnce("public int testRenamed() {")
	}
}
