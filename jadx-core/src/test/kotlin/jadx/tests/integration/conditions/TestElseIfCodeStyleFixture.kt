package jadx.tests.integration.conditions

object TestElseIfCodeStyleFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.conditions;

public class TestElseIfCodeStyleFixture {

	@SuppressWarnings("unused")
	public static class TestCls {

		public void test(String str) {
			if ("a".equals(str)) {
				call(1);
			} else if ("b".equals(str)) {
				call(2);
			} else if ("c".equals(str)) {
				call(3);
			}
		}

		private void call(int i) {
		}
	}
}
"""
}
