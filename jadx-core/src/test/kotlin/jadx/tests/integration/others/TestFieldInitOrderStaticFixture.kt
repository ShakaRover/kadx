package jadx.tests.integration.others

object TestFieldInitOrderStaticFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

import static jadx.tests.api.utils.assertj.JadxAssertions.assertThat;

public class TestFieldInitOrderStaticFixture {

	@SuppressWarnings("ConstantName")
	public static class TestCls {
		private static final StringBuilder sb = new StringBuilder();
		private static final String a = sb.append("a").toString();
		private static final String b = sb.append("b").toString();
		private static final String c = sb.append("c").toString();
		private static final String result = sb.toString();

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
