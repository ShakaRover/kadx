package kadx.tests.integration.types

object TestLongCastFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

import static org.assertj.core.api.Assertions.assertThat;

public class TestLongCastFixture {

	public static class TestCls {

		public long test(char c) {
			return (long) c << 32;
		}

		public int test2(long l) {
			return (int) l >> 2;
		}

		public void check() {
			assertThat(test((char) 22)).isEqualTo(94489280512L);
			assertThat(test2((1L << 32) + 8)).isEqualTo(2);
		}
	}
}
"""
}
