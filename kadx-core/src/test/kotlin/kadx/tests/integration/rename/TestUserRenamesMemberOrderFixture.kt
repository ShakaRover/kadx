package kadx.tests.integration.rename

object TestUserRenamesMemberOrderFixture {
	class TestCls {
		class A
		class B
	}

	const val JAVA_SOURCE = """package kadx.tests.integration.rename;

public class TestUserRenamesMemberOrderFixture {

	public static class TestCls {
		public static int z = Integer.parseInt("1");
		public static int a = Integer.parseInt("2");

		public static class A {
		}

		public static class B {
		}

		public TestCls(A a) {
		}

		public TestCls(B b) {
		}

		public TestCls(A[] a) {
		}

		public TestCls(B[] b) {
		}

		public void first() {
		}

		public void second() {
		}
	}
}
"""
}
