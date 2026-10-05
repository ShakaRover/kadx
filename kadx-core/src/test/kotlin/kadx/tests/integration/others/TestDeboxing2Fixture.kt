package kadx.tests.integration.others

object TestDeboxing2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestDeboxing2Fixture {

	public static class TestCls {
		public long test(Long l) {
			if (l == null) {
				l = 0L;
			}
			return l;
		}

		public void check() {
			assertThat(test(null)).isEqualTo(0L);
			assertThat(test(0L)).isEqualTo(0L);
			assertThat(test(7L)).isEqualTo(7L);
		}
	}
}
"""
}
