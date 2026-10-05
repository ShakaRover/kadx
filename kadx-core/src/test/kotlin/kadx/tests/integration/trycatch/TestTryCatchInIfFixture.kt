package kadx.tests.integration.trycatch

object TestTryCatchInIfFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.trycatch;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTryCatchInIfFixture {

	public static class TestCls {

		private String test(String name, String value) {
			if (value != null) {
				try {
					int key;
					if (value.startsWith("0x")) {
						value = value.substring(2);
						key = Integer.parseInt(value, 16);
					} else {
						key = Integer.parseInt(value);
					}
					return name + '=' + key;
				} catch (NumberFormatException e) {
					return "Failed to parse number";
				}
			}
			System.out.println("?");
			return null;
		}

		public void check() {
			assertThat(test("n", null)).isNull();
			assertThat(test("n", "7")).isEqualTo("n=7");
			assertThat(test("n", "0x" + Integer.toHexString(77))).isEqualTo("n=77");
			assertThat(test("n", "abc")).isEqualTo("Failed to parse number");
			assertThat(test("n", "0xabX")).isEqualTo("Failed to parse number");
		}
	}
}
"""
}
