package kadx.tests.integration.inline

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * bridge 方法内联后，调用点应改用可访问的目标类 `C`。
 */
class TestMethodInline : SmaliTest() {
	// @formatter:off
	/*
		package inline;

		public class A {
			public static void useMth() {
				inline.other.B.bridgeMth(); // after inline 'inline.other.C.test()' is not accessible
			}
		}
		-----------------------------------------------------------
		package inline.other;

		public class B {
			public static bridge synthetic void bridgeMth() {
				inline.other.C.test();
			}
		}
		----------------------------------------------------------
		package inline.other;

		class C {
			public static void test() {
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		val classes = loadFromSmaliFiles()
		val aCls = searchCls(classes, "inline.A")
		val bCls = searchCls(classes, "inline.other.B")
		val cCls = searchCls(classes, "inline.other.C")

		assertThat(bCls).code().doesNotContain("bridgeMth()")
		assertThat(aCls).code().containsOne("C.test()")
		assertThat(cCls).code().containsOne("public class C {")

		// TODO: update dependencies?
		// assertThat(aCls.getDependencies()).contains(cCls);
		// assertThat(cCls.getUsedIn()).contains(aCls);
	}
}
