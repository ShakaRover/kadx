package jadx.tests.integration.others

object TestFieldInitDifferentArgumentsFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

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
