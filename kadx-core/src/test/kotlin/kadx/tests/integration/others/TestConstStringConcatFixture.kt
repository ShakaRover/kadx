package kadx.tests.integration.others

object TestConstStringConcatFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestConstStringConcatFixture {

	@SuppressWarnings("StringBufferReplaceableByString")
	public static class TestCls {
		public String test1(int value) {
			return new StringBuilder().append("Value").append(" equals ").append(value).toString();
		}

		public String test2() {
			return new StringBuilder().append("App ").append("version: ").append(1).append('.').append(2).toString();
		}

		public String test3(String name, int value) {
			return "value " + name + " = " + value;
		}

		public void check() {
			assertThat(test1(7)).isEqualTo("Value equals 7");
			assertThat(test2()).isEqualTo("App version: 1.2");
			assertThat(test3("v", 4)).isEqualTo("value v = 4");
		}
	}
}
"""
}
