package kadx.tests.integration.types

object TestTypeResolver24Fixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.types;

public class TestTypeResolver24Fixture {

	@SuppressWarnings("DataFlowIssue")
	public static class TestCls {
		public void test() {
			((T1) null).foo1();
			((T2) null).foo2();
		}

		static class T1 {
			public void foo1() {
			}
		}

		static class T2 {
			public void foo2() {
			}
		}
	}
}
"""
}
