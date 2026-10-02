package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 验证错误的方法访问修饰符能被修正：子类以 protected 覆写抽象方法时应还原为 public。
 */
class TestBadMethodAccessModifiers : SmaliTest() {
	// @formatter:off
	/*
		public static class TestCls {

			public abstract class A {
				public abstract void test();
			}

			public class B extends A {
				protected void test() {
				}
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliFiles("others", "TestBadMethodAccessModifiers", "TestCls"))
			.code()
			.doesNotContain("protected void test() {")
			.containsOne("public void test() {")
	}
}
