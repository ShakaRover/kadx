package kadx.tests.integration.trycatch

object TestTryCatchFinallyFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

@SuppressWarnings("checkstyle:printstacktrace")
public class TestTryCatchFinallyFixture {

	public static class TestCls {
		public boolean f;

		@SuppressWarnings("ConstantConditions")
		private boolean test(Object obj) {
			this.f = false;
			try {
				exc(obj);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				this.f = true;
			}
			return this.f;
		}

		private static boolean exc(Object obj) throws Exception {
			if (obj == null) {
				throw new Exception("test");
			}
			return (obj instanceof String);
		}

		public void check() {
			assertThat(test("a")).isTrue();
			assertThat(test(null)).isTrue();
		}
	}
}
"""
}
