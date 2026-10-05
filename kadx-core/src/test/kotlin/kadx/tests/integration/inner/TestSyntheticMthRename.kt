package kadx.tests.integration.inner

import kadx.tests.api.SmaliTest
import kadx.tests.api.utils.assertj.KadxAssertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Issue: https://github.com/skylot/jadx/issues/397
 */
class TestSyntheticMthRename : SmaliTest() {

	// @formatter:off
	/*
		public class TestCls {
			public interface I<R, P> {
				R call(P... p);
			}

			public static final class A implements I<String, Runnable> {
				public synthetic virtual Object call(Object[] objArr) {
					return renamedCall((Runnable[]) objArr);
				}

				private varargs direct String renamedCall(Runnable... p) {
					return "str";
				}
			}
		}
	 */
	// @formatter:on

	@Test
	fun test() {
		assertThat(getClassNodeFromSmaliFiles("inner", "TestSyntheticMthRename", "TestCls"))
			.code()
			.containsOne("public String call(Runnable... p) {")
			.doesNotContain("synthetic")
	}
}
