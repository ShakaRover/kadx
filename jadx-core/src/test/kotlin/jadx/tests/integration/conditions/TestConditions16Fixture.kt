package jadx.tests.integration.conditions

object TestConditions16Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

import static org.assertj.core.api.Assertions.assertThat;

public class TestConditions16Fixture {

	public static class TestCls {
		private static boolean test(int a, int b) {
			return a < 0 || b % 2 != 0 && a > 28 || b < 0;
		}

		public void check() {
			assertThat(test(-1, 1)).isTrue();
			assertThat(test(1, -1)).isTrue();
			assertThat(test(29, 3)).isTrue();
			assertThat(test(2, 2)).isFalse();
		}
	}
}
"""
}
