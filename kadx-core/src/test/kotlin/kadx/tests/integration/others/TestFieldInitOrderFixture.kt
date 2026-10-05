package kadx.tests.integration.others

object TestFieldInitOrderFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestFieldInitOrderFixture {

	public static class TestCls {
		private final StringBuilder sb = new StringBuilder();
		private final String a = sb.append("a").toString();
		private final String b = sb.append("b").toString();
		private final String c = sb.append("c").toString();
		private final String result = sb.toString();

		public void check() {
			assertThat(result).isEqualTo("abc");
			assertThat(a).isEqualTo("a");
			assertThat(b).isEqualTo("ab");
			assertThat(c).isEqualTo("abc");
		}
	}
}
"""
}
