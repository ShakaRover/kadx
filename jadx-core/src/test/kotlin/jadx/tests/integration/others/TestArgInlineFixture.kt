package jadx.tests.integration.others

object TestArgInlineFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestArgInlineFixture {

	public static class TestCls {

		public void test(int a) {
			while (a < 10) {
				int b = a + 1;
				a = b;
			}
		}
	}
}
"""
}
