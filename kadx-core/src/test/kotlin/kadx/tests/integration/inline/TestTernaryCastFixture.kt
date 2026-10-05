package kadx.tests.integration.inline

object TestTernaryCastFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inline;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

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
