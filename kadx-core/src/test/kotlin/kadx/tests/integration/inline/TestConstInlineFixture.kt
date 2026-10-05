package kadx.tests.integration.inline

object TestConstInlineFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inline;

public class TestConstInlineFixture {

	public static class TestCls {
		public boolean test() {
			try {
				return f(0);
			} catch (Exception e) {
				return false;
			}
		}

		public boolean f(int i) {
			return true;
		}
	}
}
"""
}
