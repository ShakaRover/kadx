package kadx.tests.integration.others

object TestFieldAccessReorderFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestFieldAccessReorderFixture {
	public static class TestCls {
		private long field = 10;

		public final boolean test() {
			long value = longCall();
			long diff = value - this.field;
			this.field = value;
			return diff > 250;
		}

		public static long longCall() {
			return 261L;
		}

		public void check() {
			assertThat(test()).isTrue();
		}
	}
}
"""
}
