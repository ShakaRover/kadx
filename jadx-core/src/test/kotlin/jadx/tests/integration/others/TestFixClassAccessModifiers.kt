package jadx.tests.integration.others

import jadx.tests.api.SmaliTest
import jadx.tests.api.utils.assertj.JadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 修复类访问修饰符：内部类被外部类引用时应正确提升可见性。
 */
class TestFixClassAccessModifiers : SmaliTest() {
	// @formatter:off
	/*
		// class others.TestCls
		public Cls.InnerCls field;

		// class others.Cls
		public static class Cls {
			private static class InnerCls {
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		val classes = loadFromSmaliFiles()
		assertThat(searchCls(classes, "others.Cls"))
			.code()
	}
}
