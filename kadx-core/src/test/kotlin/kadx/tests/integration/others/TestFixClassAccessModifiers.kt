package kadx.tests.integration.others

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
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
