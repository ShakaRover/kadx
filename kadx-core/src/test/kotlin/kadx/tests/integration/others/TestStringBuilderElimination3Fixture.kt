package kadx.tests.integration.others

object TestStringBuilderElimination3Fixture {
	class TestCls
	class TestClsNegative

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestStringBuilderElimination3Fixture {

	public static class TestCls {
		public static String test(String a) {
			StringBuilder sb = new StringBuilder();
			sb.append("result = ");
			sb.append(a);
			return sb.toString();
		}
	}

	public static class TestClsNegative {
		private String f = "first";

		public String test() {
			StringBuilder sb = new StringBuilder();
			sb.append("before = ");
			sb.append(this.f);
			updateF();
			sb.append(", after = ");
			sb.append(this.f);
			return sb.toString();
		}

		private void updateF() {
			this.f = "second";
		}

		public void check() {
			assertThat(test()).isEqualTo("before = first, after = second");
		}
	}
}
"""
}
