package kadx.tests.integration.trycatch

object TestTryCatchFinally7Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestTryCatchFinally7Fixture {

	public static class TestCls {
		private int f = 0;

		private boolean test(Object obj) {
			boolean res;
			try {
				res = exc(obj);
			} catch (Exception e) {
				res = false;
			} finally {
				f++;
			}
			return res;
		}

		private boolean exc(Object obj) throws Exception {
			if ("r".equals(obj)) {
				throw new AssertionError();
			}
			return true;
		}

		public void check() {
			f = 0;
			assertThat(test(null)).isTrue();
			assertThat(f).isEqualTo(1);

			f = 0;
			try {
				test("r");
			} catch (AssertionError e) {
				// pass
			}
			assertThat(f).isEqualTo(1);
		}
	}
}
"""
}
