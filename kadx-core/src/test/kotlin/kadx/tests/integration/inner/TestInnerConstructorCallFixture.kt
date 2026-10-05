package kadx.tests.integration.inner

object TestInnerConstructorCallFixture {
	class TestCls

	const val JAVA_SOURCE = """package kadx.tests.integration.inner;

public class TestInnerConstructorCallFixture {

	public static class TestCls {
		@SuppressWarnings("InnerClassMayBeStatic")
		public class A {
			public class AA {
				public void test() {
				}
			}
		}

		public void test() {
			A a = new A();
			A.AA aa = a.new AA();
			aa.test();
		}
	}
}
"""
}
