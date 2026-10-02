package jadx.tests.integration.deobf;

public class TestRenameOverriddenMethod2Fixture {

	public static class TestCls {

		public interface I {
			int call();
		}

		public static class A implements I {
			@Override
			public int call() {
				return 1;
			}
		}

		public static class B implements I {
			@Override
			public int call() {
				return 2;
			}
		}
	}
}
