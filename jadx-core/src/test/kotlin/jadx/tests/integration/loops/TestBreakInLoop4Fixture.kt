package jadx.tests.integration.loops

object TestBreakInLoop4Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.loops;

import static org.assertj.core.api.Assertions.assertThat;

public class TestBreakInLoop4Fixture {

	public static class TestCls {

		public double test(char c) {
			double m = 1.0;
			for (int i = 0; i < 5; i++) {
				if (c != '.') {
					if (c == 'a' || c == 'b') {
						m = 1024.0;
					}
					break;
				}
			}
			return m;
		}

		public void check() {
			assertThat(test('.')).isEqualTo(1.0);
			assertThat(test('a')).isEqualTo(1024.0);
			assertThat(test('b')).isEqualTo(1024.0);
			assertThat(test('c')).isEqualTo(1.0);
		}
	}
}
"""
}
