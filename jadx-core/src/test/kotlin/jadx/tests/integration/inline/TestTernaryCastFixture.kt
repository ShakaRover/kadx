package jadx.tests.integration.inline

object TestTernaryCastFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inline;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestTernaryCastFixture {

	public static class TestCls {
		public String test(boolean b, Object obj, CharSequence cs) {
			return (String) (b ? obj : cs);
		}

		public void check() {
			assertThat(test(true, "a", "b")).isEqualTo("a");
			assertThat(test(false, "a", "b")).isEqualTo("b");
		}
	}
}
"""
}
