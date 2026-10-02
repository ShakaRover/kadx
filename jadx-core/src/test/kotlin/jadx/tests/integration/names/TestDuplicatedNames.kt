package jadx.tests.integration.names

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 重名成员：同名的字段与方法（返回类型不同）需要重命名区分，反编译结果不应出现歧义。
 */
class TestDuplicatedNames : SmaliTest() {

	// @formatter:off
	/*
		public static class TestCls {

			public Object fieldName;
			public String fieldName;

			public Object run() {
				return this.fieldName;
			}

			public String run() {
				return this.fieldName;
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		commonChecks()
	}

	@Test
	fun testWithDeobf() {
		enableDeobfuscation()
		commonChecks()
	}

	private fun commonChecks() {
		assertThat(getClassNodeFromSmaliWithPath("names", "TestDuplicatedNames"))
			.code()
			.containsOne("Object fieldName;")
			.containsOne("String f0fieldName")
			.containsOne("this.fieldName")
			.containsOne("this.f0fieldName")
			.containsOne("public Object run() {")
			.containsOne("public String m0run() {")
	}
}
