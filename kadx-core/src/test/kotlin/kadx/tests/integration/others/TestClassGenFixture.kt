package kadx.tests.integration.others

object TestClassGenFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.others;

public class TestClassGenFixture {

	public static class TestCls {
		public interface I {
			int test();

			public int test3();
		}

		public abstract static class A {
			public abstract int test2();
		}
	}
}
"""
}
