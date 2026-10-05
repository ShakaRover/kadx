package kadx.tests.integration.trycatch

object TestFinallyExtractFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import static org.assertj.core.api.Assertions.assertThat;

public class TestFinallyExtractFixture {

	public static class TestCls {
		private int result = 0;

		public String test() {
			boolean success = false;
			try {
				String value = call();
				result++;
				success = true;
				return value;
			} finally {
				if (!success) {
					result -= 2;
				}
			}
		}

		private String call() {
			return "call";
		}

		public void check() {
			test();
			assertThat(result).isEqualTo(1);
		}
	}
}
"""
}
