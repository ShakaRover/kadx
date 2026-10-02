package jadx.tests.integration.deobf;

public class TestInheritedMethodRenameFixture {

	public static class TestCls {

		public static class A extends B {
		}

		public static class B {
			public void call() {
				System.out.println("call");
			}
		}

		public void test(A a) {
			// reference to A.call() not renamed,
			// should be resolved to B.call() and use alias
			a.call();
		}
	}
}
