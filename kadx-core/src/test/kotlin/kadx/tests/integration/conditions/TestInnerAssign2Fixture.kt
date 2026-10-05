package kadx.tests.integration.conditions

object TestInnerAssign2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.conditions;

import static kadx.tests.api.utils.assertj.KadxAssertions.assertThat;

public class TestInnerAssign2Fixture {

	public static class TestCls {
		private String field;
		private String swapField;

		@SuppressWarnings("checkstyle:InnerAssignment")
		public boolean test(String str) {
			String sub;
			return call(str) || ((sub = this.field) != null && sub.isEmpty());
		}

		private boolean call(String str) {
			this.field = swapField;
			return str.isEmpty();
		}

		public boolean testWrap(String str, String fieldValue) {
			this.field = null;
			this.swapField = fieldValue;
			return test(str);
		}

		public void check() {
			assertThat(testWrap("", null)).isTrue();
			assertThat(testWrap("a", "")).isTrue();
			assertThat(testWrap("b", null)).isFalse();
			assertThat(testWrap("c", "d")).isFalse();
		}
	}
}
"""
}
