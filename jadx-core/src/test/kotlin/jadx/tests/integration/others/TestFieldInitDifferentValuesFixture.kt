package jadx.tests.integration.others

object TestFieldInitDifferentValuesFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestFieldInitDifferentValuesFixture {

	public static class TestCls {
		public final int value;

		public TestCls() {
			this.value = 1;
		}

		public TestCls(int ignored) {
			this.value = 2;
		}

		public void check() {
			assertThat(new TestCls().value).isEqualTo(1);
			assertThat(new TestCls(0).value).isEqualTo(2);
		}
	}
}
"""
}
