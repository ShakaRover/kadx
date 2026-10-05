package kadx.tests.integration.others

object TestFieldInitDifferentArgumentsFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestFieldInitDifferentArgumentsFixture {

	public static class TestCls {
		public final String value;

		public TestCls() {
			this.value = String.valueOf(Math.abs(-1));
		}

		public TestCls(int ignored) {
			this.value = String.valueOf(Math.abs(-2));
		}

		public void check() {
			assertThat(new TestCls().value).isEqualTo("1");
			assertThat(new TestCls(0).value).isEqualTo("2");
		}
	}
}
"""
}
