package jadx.tests.integration.usethis

object TestInlineThisFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.usethis;

public class TestInlineThisFixture {

	public static class TestCls {
		public int field;

		public void test() {
			TestCls something = this;
			something.method();
			something.field = 123;
		}

		private void method() {
		}
	}
}
"""
}
