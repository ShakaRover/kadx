package jadx.tests.integration.others

import jadx.tests.api.IntegrationTest
import jadx.tests.api.utils.assertj.JadxAssertions
import org.junit.jupiter.api.Test

/**
 * String 构造器：字节/字符数组常量的构造应尽量还原为字符串字面量，
 * 无法还原时保留 `new String(...)` 形式。
 */
class TestStringConstructor : IntegrationTest() {

	@Test
	fun test() {
		JadxAssertions.assertThat(getClassNode(TestStringConstructorFixture.TestCls::class.java))
			.code()
			.containsOne("abc")
	}

	@Test
	fun test2() {
		JadxAssertions.assertThat(getClassNode(TestStringConstructorFixture.TestCls2::class.java))
			.code()
			.containsOne("new String(\"abc\".getBytes(), StandardCharsets.UTF_8)")
	}

	@Test
	fun test3() {
		JadxAssertions.assertThat(getClassNode(TestStringConstructorFixture.TestCls3::class.java))
			.code()
			.containsOne("\\u0001\\u0002\\u0003abc")
	}

	@Test
	fun test4() {
		JadxAssertions.assertThat(getClassNode(TestStringConstructorFixture.TestCls4::class.java))
			.code()
			.containsOne("\\u0001\\u0002\\u0003abc")
	}

	@Test
	fun test5() {
		JadxAssertions.assertThat(getClassNode(TestStringConstructorFixture.TestCls5::class.java))
			.code()
			.containsOne("{1, 2, 3, 'a', 'b'}")
	}

	@Test
	fun testNegative() {
		JadxAssertions.assertThat(getClassNode(TestStringConstructorFixture.TestClsNegative::class.java))
			.code()
			.containsOne("tag = new String();")
	}

	@Test
	fun testNegative2() {
		JadxAssertions.assertThat(getClassNode(TestStringConstructorFixture.TestClsNegative2::class.java))
			.code()
			.containsOne("tag = new String(new byte[]{31, this.b});")
	}
}
