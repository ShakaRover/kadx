package kadx.tests.integration.others

object TestCodeMetadataFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestCodeMetadataFixture {

	public static class TestCls {
		public static class A {
			public String str;
		}

		public String test() {
			A a = new A();
			a.str = call();
			return a.str;
		}

		public static String call() {
			return "str";
		}
	}
}
"""
}
