package jadx.tests.integration.deobf;

public class TestRenameOverriddenMethod3Fixture {

	public static class TestCls {

		public abstract static class A {
			public abstract int call();
		}

		public static class B extends A {
			@Override
			public final int call() {
				return 1;
			}
		}
	}
}
