package kadx.tests.integration.others

object TestDeboxing4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestDeboxing4Fixture {

	public static class TestCls {

		public boolean test(Integer i) {
			return ((Integer) 1).equals(i);
		}

		public void check() {
			assertThat(test(null)).isFalse();
			assertThat(test(0)).isFalse();
			assertThat(test(1)).isTrue();
		}
	}
}
"""
}
