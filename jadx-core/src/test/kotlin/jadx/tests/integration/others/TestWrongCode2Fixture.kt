package jadx.tests.integration.others

object TestWrongCode2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.others;

public class TestWrongCode2Fixture {

	public static class TestCls {
		@SuppressWarnings("ConstantConditions")
		public String test() {
			A a = null;
			a.str = "";
			return a.str;
		}

		@SuppressWarnings("ConstantConditions")
		public int test2() {
			int[] a = null;
			a[1] = 2;
			return a[0];
		}

		@SuppressWarnings({ "ConstantConditions", "SynchronizationOnLocalVariableOrMethodParameter" })
		public boolean test3() {
			A a = null;
			synchronized (a) {
				return true;
			}
		}

		public boolean test4() {
			return null instanceof A;
		}

		// everything is 'A' :)
		@SuppressWarnings({ "MethodName", "LocalVariableName" }) // ignore checkstyle
		public A A() {
			A A = A();
			A.A = A;
			return A;
		}

		@SuppressWarnings("MemberName")
		public static class A {
			public String str;
			public A A;
		}
	}
}
"""
}
