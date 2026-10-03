package jadx.tests.integration.inline

object TestSyntheticInline2Fixture {
	class TestCls

	const val JAVA_SOURCE = """package jadx.tests.integration.inline;

public class TestSyntheticInline2Fixture {

	public static class Base {
		protected void call() {
			System.out.println("base call");
		}
	}

	public static class TestCls extends Base {
		public class A {
			public void invokeCall() {
				TestCls.this.call();
			}

			public void invokeSuperCall() {
				TestCls.super.call();
			}
		}

		@Override
		public void call() {
			System.out.println("TestCls call");
		}

		public void check() {
			A a = new A();
			a.invokeSuperCall();
			a.invokeCall();
		}
	}
}
"""
}
