package kadx.tests.integration.trycatch

object TestTryCatch9Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestTryCatch9Fixture {

	public static class TestCls {
		public Integer test(final Integer i) {
			if (i == null) {
				return null;
			}
			Integer res = null;
			try {
				if (i == 5) {
					res = 4;
				} else {
					res = 9;
				}
			} catch (final Exception ex) {
				logError(ex);
			}
			return res;
		}

		private void logError(Exception ex) {
		}

		public void check() {
			assertThat(test(5)).isEqualTo(4);
		}
	}
}
"""
}
