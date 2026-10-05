package kadx.tests.integration.inline

object TestInline3Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inline;

public class TestInline3Fixture {

	public static class TestCls {
		public TestCls(int b1, int b2) {
			this(b1, b2, 0, 0, 0);
		}

		public TestCls(int a1, int a2, int a3, int a4, int a5) {
		}

		public class A extends TestCls {
			public A(int a) {
				super(a, a);
			}
		}
	}
}
"""
}
