package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 包私有方法覆写：跨包覆写不应被标记为 `@Override`，且不应修改原方法的访问标志。
 */
class TestOverridePackagePrivateMethod : SmaliTest() {
	// @formatter:off
	/*
		-----------------------------------------------------------
		package test;

		public class A {
			void a() { // package-private
			}
		}
		-----------------------------------------------------------
		package test;

		public class B extends A {
			@Override // test.A
			public void a() {
			}
		}
		-----------------------------------------------------------
		package other;

		import test.A;

		public class C extends A {
			// No @Override here
			public void a() {
			}
		}
		-----------------------------------------------------------
	 */
	// @formatter:on

	@Test
	fun test() {
		commonChecks()
	}

	@Test
	fun testDontChangeAccFlags() {
		getArgs().isRespectBytecodeAccModifiers = true
		commonChecks()
	}

	private fun commonChecks() {
		val classes = loadFromSmaliFiles()
		assertThat(searchCls(classes, "test.A"))
			.code()
			.doesNotContain("/* access modifiers changed")
			.containsLine(1, "void a() {")

		assertThat(searchCls(classes, "test.B")).code().containsOne("@Override")
		assertThat(searchCls(classes, "other.C")).code().doesNotContain("@Override")
	}
}
