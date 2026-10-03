package jadx.tests.integration.android

object TestRFieldRestore2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.android;

public class TestRFieldRestore2Fixture {

	public static class TestCls {

		public static class R {
		}

		public int test() {
			return 2131230730;
		}
	}
}
"""
}
