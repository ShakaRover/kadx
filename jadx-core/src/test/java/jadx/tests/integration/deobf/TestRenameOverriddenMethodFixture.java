package jadx.tests.integration.deobf;

public class TestRenameOverriddenMethodFixture {

	public static class TestCls {
		public interface I {
			void m();
		}

		public static class A implements I {
			@Override
			public void m() {
			}
		}

		public static class B extends A {
			@Override
			public void m() {
			}
		}
	}
}
