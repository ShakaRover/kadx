package jadx.tests.integration.inline

object TestInlineFixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inline;

public class TestInlineFixture {

	public static class TestCls {
		public static void main(String[] args) throws Exception {
			System.out.println("Test: " + new TestCls().testRun());
		}

		private boolean testRun() {
			return false;
		}
	}
}
"""
}
